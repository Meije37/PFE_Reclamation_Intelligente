package com.pfe.backend.service;

import com.pfe.backend.entity.*;
import com.pfe.backend.entity.enums.Priorite;
import com.pfe.backend.entity.enums.StatutReclamation;
import com.pfe.backend.entity.enums.TypeFeedbackIA;
import com.pfe.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Indicateurs des deux tableaux de bord (administrateur et agent), calculés en une seule fois.
 *
 * Administrateur : chiffres clés, bloc « À surveiller » (ce qui demande une action maintenant,
 * dont trois cas signalés par l'IA).
 * Agent : son travail personnel (à traiter, en cours, délai moyen, retards), ses réclamations
 * urgentes, la répartition par quartier et les derniers avis des citoyens.
 */
@Service
@RequiredArgsConstructor
public class TableauDeBordService {

    /** Retard : une réclamation Critique non résolue après 1 jour, Haute après 3 jours. */
    public static final int RETARD_CRITIQUE_JOURS = 1;
    public static final int RETARD_HAUTE_JOURS = 3;

    /** Un avis négatif = 1 ou 2 étoiles, compté sur les 30 derniers jours. */
    private static final int NOTE_NEGATIVE_MAX = 2;
    private static final int FENETRE_AVIS_JOURS = 30;

    private final ReclamationRepository reclamationRepository;
    private final AffectationRepository affectationRepository;
    private final HistoriqueStatutRepository historiqueRepository;
    private final EvaluationRepository evaluationRepository;
    private final FeedbackIARepository feedbackRepository;
    private final SupervisionIAService supervisionService;
    private final DoublonIAService doublonService;

    // ── Administrateur ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> apercuAdmin() {
        List<Reclamation> toutes = reclamationRepository.findAll();
        Map<StatutReclamation, Long> parStatut = toutes.stream()
                .filter(r -> r.getStatut() != null)
                .collect(Collectors.groupingBy(Reclamation::getStatut, Collectors.counting()));
        long resolues = parStatut.getOrDefault(StatutReclamation.RESOLUE, 0L);
        long rejetees = parStatut.getOrDefault(StatutReclamation.REJETEE, 0L);

        LocalDateTime ilYa7Jours = LocalDateTime.now().minusDays(7);
        long resoluesSemaine = historiqueRepository.findAll().stream()
                .filter(h -> h.getNouveauStatut() == StatutReclamation.RESOLUE
                        && h.getDateChangement() != null && h.getDateChangement().isAfter(ilYa7Jours))
                .map(h -> h.getReclamation().getId())
                .distinct().count();
        long horsPerimetre = feedbackRepository
                .findByTypeDecisionInOrderByDateDecisionAsc(List.of(TypeFeedbackIA.HORS_PERIMETRE_CONFIRME)).size();

        Map<String, Object> chiffres = new LinkedHashMap<>();
        chiffres.put("total", toutes.size());
        chiffres.put("ouvertes", parStatut.getOrDefault(StatutReclamation.OUVERTE, 0L));
        chiffres.put("enCours", parStatut.getOrDefault(StatutReclamation.EN_COURS, 0L));
        chiffres.put("resolues", resolues);
        chiffres.put("rejetees", rejetees);
        chiffres.put("resoluesSemaine", resoluesSemaine);
        chiffres.put("rejeteesHorsPerimetre", horsPerimetre);
        // Taux calculé sur les dossiers tranchés (résolus ou rejetés), pas sur le total :
        // une réclamation encore ouverte n'est ni un succès ni un échec.
        chiffres.put("tauxResolution", resolues + rejetees == 0 ? null
                : Math.round(100.0 * resolues / (resolues + rejetees)));

        Set<Long> affectees = affectationRepository.findAll().stream()
                .map(a -> a.getReclamation().getId()).collect(Collectors.toSet());
        LocalDateTime ilYa30Jours = LocalDateTime.now().minusDays(FENETRE_AVIS_JOURS);

        Map<String, Object> aSurveiller = new LinkedHashMap<>();
        aSurveiller.put("critiquesEnCours", toutes.stream()
                .filter(r -> nonClose(r) && r.getPriorite() == Priorite.CRITIQUE).count());
        aSurveiller.put("perimetreAVerifier", supervisionService.perimetreAVerifier().size());
        aSurveiller.put("categorieAVerifier", supervisionService.categorieAVerifier().size());
        aSurveiller.put("doublonsProposes", doublonService.aVerifier().size());
        aSurveiller.put("avisNegatifs", evaluationRepository.findAll().stream()
                .filter(e -> e.getNote() != null && e.getNote() <= NOTE_NEGATIVE_MAX
                        && e.getDateEvaluation() != null && e.getDateEvaluation().isAfter(ilYa30Jours))
                .count());
        aSurveiller.put("nonAffectees", toutes.stream()
                .filter(r -> nonClose(r) && !affectees.contains(r.getId())).count());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chiffres", chiffres);
        m.put("aSurveiller", aSurveiller);
        return m;
    }

    // ── Agent ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> apercuAgent(Utilisateur agent) {
        Map<Reclamation, LocalDateTime> mesReclamations = mesReclamations(agent);

        LocalDate aujourdHui = LocalDate.now();
        LocalDateTime debutMois = aujourdHui.withDayOfMonth(1).atStartOfDay();
        LocalDateTime ilYa7Jours = LocalDateTime.now().minusDays(7);

        long aTraiter = 0, enCours = 0, resoluesMois = 0, resoluesSemaine = 0;
        List<Double> delais = new ArrayList<>();
        Map<String, Long> parQuartier = new HashMap<>();
        List<Map<String, Object>> urgentes = new ArrayList<>();
        List<Map<String, Object>> aVerifierIA = new ArrayList<>();
        long prioritesAConfirmer = 0, iaPeuSure = 0, categoriesAVerifier = 0;

        for (Map.Entry<Reclamation, LocalDateTime> e : mesReclamations.entrySet()) {
            Reclamation r = e.getKey();
            LocalDateTime affectee = e.getValue() != null ? e.getValue() : r.getDateCreation();
            StatutReclamation s = r.getStatut();
            if (s == StatutReclamation.OUVERTE) aTraiter++;
            if (s == StatutReclamation.EN_COURS) enCours++;

            if (s == StatutReclamation.RESOLUE) {
                LocalDateTime resolution = dateResolution(r);
                if (resolution != null) {
                    if (!resolution.isBefore(debutMois)) resoluesMois++;
                    if (resolution.isAfter(ilYa7Jours)) resoluesSemaine++;
                    if (affectee != null && resolution.isAfter(affectee)) {
                        delais.add(Duration.between(affectee, resolution).toMinutes() / 1440.0);
                    }
                }
            }

            if (nonClose(r)) {
                parQuartier.merge(quartier(r), 1L, Long::sum);
                if (r.getPriorite() == Priorite.CRITIQUE || r.getPriorite() == Priorite.HAUTE) {
                    urgentes.add(ligneUrgente(r, affectee));
                }
                // Ce que l'IA demande à l'agent de vérifier sur ses propres dossiers.
                boolean prioriteAConfirmer = !Boolean.TRUE.equals(r.getPrioriteValidee());
                boolean peuSure = prioriteAConfirmer && r.getConfiancePrioriteIA() != null
                        && r.getConfiancePrioriteIA() < SupervisionIAService.SEUIL_PRIORITE_INCERTAINE;
                boolean categorie = Boolean.TRUE.equals(r.getCategorieAVerifier());
                if (prioriteAConfirmer) prioritesAConfirmer++;
                if (peuSure) iaPeuSure++;
                if (categorie) categoriesAVerifier++;
                if (categorie || prioriteAConfirmer) aVerifierIA.add(ligneIA(r, categorie, peuSure));
            }
        }

        // Critique avant Haute, puis le score d'urgence de l'IA le plus élevé.
        urgentes.sort(Comparator
                .comparing((Map<String, Object> l) -> "CRITIQUE".equals(l.get("priorite")) ? 0 : 1)
                .thenComparing(l -> -((Number) l.getOrDefault("scoreUrgence", 0)).doubleValue()));

        List<Map<String, Object>> quartiers = parQuartier.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(q -> {
                    Map<String, Object> l = new LinkedHashMap<>();
                    l.put("quartier", q.getKey());
                    l.put("total", q.getValue());
                    return l;
                })
                .toList();

        // Mes décisions sur les propositions de l'IA (elles servent à la réentraîner).
        List<FeedbackIA> mesDecisions = feedbackRepository.findAll().stream()
                .filter(f -> f.getAuteur() != null && agent.getId().equals(f.getAuteur().getId()))
                .toList();
        long prioritesDecidees = mesDecisions.stream().filter(f -> f.getTypeDecision() == TypeFeedbackIA.PRIORITE_CONFIRMEE
                || f.getTypeDecision() == TypeFeedbackIA.PRIORITE_CORRIGEE).count();
        long prioritesConfirmees = mesDecisions.stream()
                .filter(f -> f.getTypeDecision() == TypeFeedbackIA.PRIORITE_CONFIRMEE).count();

        // Catégorie à vérifier d'abord, puis l'IA la moins sûre d'elle.
        aVerifierIA.sort(Comparator
                .comparing((Map<String, Object> l) -> Boolean.TRUE.equals(l.get("categorieAVerifier")) ? 0 : 1)
                .thenComparing(l -> l.get("confiance") == null ? 1.0 : ((Number) l.get("confiance")).doubleValue()));

        Map<String, Object> ia = new LinkedHashMap<>();
        ia.put("prioritesAConfirmer", prioritesAConfirmer);
        ia.put("iaPeuSure", iaPeuSure);
        ia.put("categoriesAVerifier", categoriesAVerifier);
        ia.put("mesDecisions", mesDecisions.size());
        ia.put("accordPriorite", prioritesDecidees == 0 ? null : Math.round(100.0 * prioritesConfirmees / prioritesDecidees));
        ia.put("total", aVerifierIA.size());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ia", ia);
        m.put("agentPrenom", agent.getPrenom());
        m.put("agentNom", agent.getNom());
        m.put("serviceNom", agent.getService() != null ? agent.getService().getNom() : null);
        m.put("aTraiter", aTraiter);
        m.put("enCours", enCours);
        m.put("resoluesMois", resoluesMois);
        m.put("resoluesSemaine", resoluesSemaine);
        m.put("delaiMoyenJours", delais.isEmpty() ? null
                : Math.round(10 * delais.stream().mapToDouble(Double::doubleValue).average().orElse(0)) / 10.0);
        m.put("urgentes", urgentes);
        m.put("parQuartier", quartiers);
        m.put("retardCritiqueJours", RETARD_CRITIQUE_JOURS);
        m.put("retardHauteJours", RETARD_HAUTE_JOURS);
        return m;
    }

    private Map<String, Object> ligneUrgente(Reclamation r, LocalDateTime affectee) {
        long jours = affectee == null ? 0 : Math.max(0, Duration.between(affectee, LocalDateTime.now()).toDays());
        int seuil = r.getPriorite() == Priorite.CRITIQUE ? RETARD_CRITIQUE_JOURS : RETARD_HAUTE_JOURS;
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("id", r.getId());
        l.put("reference", r.getReference());
        l.put("titre", r.getTitre());
        l.put("quartier", quartier(r));
        l.put("priorite", r.getPriorite().name());
        l.put("statut", r.getStatut().name());
        l.put("scoreUrgence", r.getScoreUrgence() != null ? r.getScoreUrgence() : 0.0);
        l.put("dateAffectation", affectee != null ? affectee.toString() : null);
        l.put("ancienneteJours", jours);
        l.put("enRetard", jours > seuil);
        return l;
    }

    /**
     * Page « Vérifications IA » de l'agent : toutes ses réclamations non closes dont la priorité
     * proposée par l'IA n'est pas encore confirmée, ou dont la catégorie est à vérifier.
     * Les cas où l'IA est la moins sûre d'elle passent en premier.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> verificationsAgent(Utilisateur agent) {
        List<Map<String, Object>> priorites = new ArrayList<>();
        List<Map<String, Object>> categories = new ArrayList<>();
        for (Reclamation r : mesReclamations(agent).keySet()) {
            if (!nonClose(r)) continue;
            boolean prioriteAConfirmer = !Boolean.TRUE.equals(r.getPrioriteValidee());
            boolean peuSure = prioriteAConfirmer && r.getConfiancePrioriteIA() != null
                    && r.getConfiancePrioriteIA() < SupervisionIAService.SEUIL_PRIORITE_INCERTAINE;
            boolean categorie = Boolean.TRUE.equals(r.getCategorieAVerifier());
            if (prioriteAConfirmer) priorites.add(ligneIA(r, categorie, peuSure));
            if (categorie) categories.add(ligneIA(r, true, peuSure));
        }
        Comparator<Map<String, Object>> moinsSureDabord = Comparator
                .comparing(l -> l.get("confiance") == null ? 1.0 : ((Number) l.get("confiance")).doubleValue());
        priorites.sort(moinsSureDabord);
        categories.sort(moinsSureDabord);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("priorites", priorites);
        m.put("categories", categories);
        m.put("seuilIncertitude", SupervisionIAService.SEUIL_PRIORITE_INCERTAINE);
        return m;
    }

    /**
     * Réclamations dont l'agent est le responsable ACTUEL (sa dernière affectation est la plus récente),
     * avec la date de cette affectation. Une réclamation transférée à un autre service n'en fait plus partie.
     */
    private Map<Reclamation, LocalDateTime> mesReclamations(Utilisateur agent) {
        Map<Reclamation, LocalDateTime> mes = new LinkedHashMap<>();
        Set<Long> vues = new HashSet<>();
        for (Affectation a : affectationRepository.findByAgentId(agent.getId())) {
            Reclamation r = a.getReclamation();
            if (r == null || !vues.add(r.getId())) continue;
            affectationRepository.findByReclamationIdOrderByDateAffectationDesc(r.getId()).stream()
                    .findFirst()
                    .filter(derniere -> derniere.getAgent() != null && agent.getId().equals(derniere.getAgent().getId()))
                    .ifPresent(derniere -> mes.put(r, derniere.getDateAffectation()));
        }
        return mes;
    }

    private Map<String, Object> ligneIA(Reclamation r, boolean categorie, boolean peuSure) {
        Priorite proposee = r.getPrioriteIA() != null ? r.getPrioriteIA() : r.getPriorite();
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("id", r.getId());
        l.put("reference", r.getReference());
        l.put("titre", r.getTitre());
        l.put("prioriteIA", proposee != null ? proposee.name() : null);
        l.put("confiance", r.getConfiancePrioriteIA());
        l.put("iaPeuSure", peuSure);
        l.put("alerteCritique", Boolean.TRUE.equals(r.getAlerteCritiqueIA()));
        l.put("categorieAVerifier", categorie);
        l.put("categorieActuelle", r.getCategorie() != null ? r.getCategorie().getNom() : null);
        l.put("categorieSuggeree", r.getCategorieSuggereeNom());
        l.put("categorieActuelleId", r.getCategorie() != null ? r.getCategorie().getIdCategorie() : null);
        l.put("categorieSuggereeId", r.getCategorieSuggereeId());
        l.put("priorite", r.getPriorite() != null ? r.getPriorite().name() : null);
        l.put("statut", r.getStatut() != null ? r.getStatut().name() : null);
        l.put("description", r.getDescription());
        return l;
    }

    /** Date du passage au statut « résolue » (le plus récent), d'après l'historique. */
    private LocalDateTime dateResolution(Reclamation r) {
        return historiqueRepository.findByReclamationIdOrderByDateChangementDesc(r.getId()).stream()
                .filter(h -> h.getNouveauStatut() == StatutReclamation.RESOLUE)
                .map(HistoriqueStatut::getDateChangement)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** Moughataa calculée d'après la position GPS ; à défaut, le quartier saisi. */
    private static String quartier(Reclamation r) {
        Localisation l = r.getLocalisation();
        if (l == null) return Moughataas.INCONNUE;
        if (Moughataas.dansNouakchott(l.getLatitude(), l.getLongitude())) {
            return Moughataas.de(l.getLatitude(), l.getLongitude());
        }
        return l.getQuartier() != null && !l.getQuartier().isBlank() ? l.getQuartier() : Moughataas.de(l.getLatitude(), l.getLongitude());
    }

    private static boolean nonClose(Reclamation r) {
        return r.getStatut() != null && !r.getStatut().estClos();
    }
}
