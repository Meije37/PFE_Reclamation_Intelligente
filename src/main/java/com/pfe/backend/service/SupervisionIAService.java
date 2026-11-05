package com.pfe.backend.service;

import com.pfe.backend.dto.ChangerStatutRequestDTO;
import com.pfe.backend.entity.*;
import com.pfe.backend.entity.enums.Priorite;
import com.pfe.backend.entity.enums.TypeAnalyseIA;
import com.pfe.backend.entity.enums.TypeFeedbackIA;
import com.pfe.backend.exception.ResourceNotFoundException;
import com.pfe.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Human-in-the-Loop pour les deux autres modèles de l'IA (les doublons sont dans DoublonIAService) :
 *
 *  1. Périmètre (modèle is_municipal) : quand l'IA dit « hors périmètre » ou n'est pas sûre (< 90 %),
 *     un administrateur tranche : « C'est bien municipal » ou « Hors périmètre » (la réclamation est
 *     alors rejetée avec un motif, et le citoyen est prévenu).
 *  2. Priorité : un agent responsable ou un administrateur confirme ou corrige la priorité proposée.
 *
 * Chaque décision est enregistrée dans FeedbackIA (proposition de l'IA + décision humaine) et exportée
 * en CSV (dataset_v2) pour mesurer l'IA sur des cas RÉELS et la réentraîner.
 */
@Service
@RequiredArgsConstructor
public class SupervisionIAService {

    public static final String MUNICIPAL = "MUNICIPAL";
    public static final String HORS_PERIMETRE = "HORS_PERIMETRE";

    static final List<TypeFeedbackIA> DECISIONS_PERIMETRE =
            List.of(TypeFeedbackIA.PERIMETRE_MUNICIPAL_CONFIRME, TypeFeedbackIA.HORS_PERIMETRE_CONFIRME);
    static final List<TypeFeedbackIA> DECISIONS_PRIORITE =
            List.of(TypeFeedbackIA.PRIORITE_CONFIRMEE, TypeFeedbackIA.PRIORITE_CORRIGEE);
    static final List<TypeFeedbackIA> DECISIONS_CATEGORIE =
            List.of(TypeFeedbackIA.CATEGORIE_CONFIRMEE, TypeFeedbackIA.CATEGORIE_CORRIGEE);
    /** En dessous : l'IA hésite sur la priorité (affiché « IA peu sûre »). */
    public static final double SEUIL_PRIORITE_INCERTAINE = 0.5;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ReclamationRepository reclamationRepository;
    private final FeedbackIARepository feedbackRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AffectationRepository affectationRepository;
    private final AnalyseIARepository analyseIARepository;
    private final NotificationService notificationService;
    private final ReclamationService reclamationService;
    private final CategorieReclamationRepository categorieRepository;

    // ── 1. Périmètre municipal ─────────────────────────────────────────────

    /** Réclamations encore ouvertes dont le périmètre doit être vérifié. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> perimetreAVerifier() {
        return reclamationRepository.findByPerimetreAVerifierTrueOrderByDateCreationDesc().stream()
                .filter(r -> r.getStatut() == null || !r.getStatut().estClos())
                .map(this::resume)
                .toList();
    }

    @Transactional
    public void confirmerMunicipal(Long reclamationId, String emailAdmin, String commentaire) {
        Reclamation r = aVerifier(reclamationId);
        enregistrer(r, TypeFeedbackIA.PERIMETRE_MUNICIPAL_CONFIRME, verdictIA(r), MUNICIPAL,
                r.getProbabiliteMunicipal(), emailAdmin, commentaire);
        r.setPerimetreAVerifier(false);
        reclamationRepository.save(r);
    }

    /** Hors périmètre confirmé : la réclamation est rejetée avec un motif lisible par le citoyen. */
    @Transactional
    public void confirmerHorsPerimetre(Long reclamationId, String emailAdmin, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Indiquez au citoyen à qui s'adresser (motif obligatoire).");
        }
        Reclamation r = aVerifier(reclamationId);
        enregistrer(r, TypeFeedbackIA.HORS_PERIMETRE_CONFIRME, verdictIA(r), HORS_PERIMETRE,
                r.getProbabiliteMunicipal(), emailAdmin, motif);
        r.setPerimetreAVerifier(false);
        reclamationRepository.save(r);

        // Même chemin qu'un rejet normal : historique + notification du citoyen + doublons rattachés.
        ChangerStatutRequestDTO dto = new ChangerStatutRequestDTO();
        dto.setStatut("REJETEE");
        dto.setCommentaire("Hors du périmètre de la commune : " + motif.trim());
        reclamationService.changerStatut(r.getId(), dto);
    }

    // ── 2. Priorité ────────────────────────────────────────────────────────

    /**
     * Confirme (même valeur) ou corrige la priorité. Le contrôle d'accès (admin, ou agent
     * responsable du dossier) est fait par le contrôleur appelant.
     */
    @Transactional
    public Map<String, Object> validerPriorite(Long reclamationId, String emailAuteur,
                                               String nouvelle, String commentaire) {
        Reclamation r = reclamation(reclamationId);
        if (r.getStatut() != null && r.getStatut().estClos()) {
            throw new IllegalArgumentException("Ce dossier est clos : sa priorité ne peut plus être modifiée.");
        }
        Priorite choisie;
        try {
            choisie = Priorite.valueOf(nouvelle == null ? "" : nouvelle.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Priorité invalide : BASSE, MOYENNE, HAUTE ou CRITIQUE.");
        }

        // Référence = proposition de l'IA ; si l'IA était injoignable, la priorité de secours.
        Priorite proposee = r.getPrioriteIA() != null ? r.getPrioriteIA() : r.getPriorite();
        Priorite avant = r.getPriorite();
        TypeFeedbackIA type = choisie == proposee ? TypeFeedbackIA.PRIORITE_CONFIRMEE : TypeFeedbackIA.PRIORITE_CORRIGEE;

        Utilisateur auteur = enregistrer(r, type, proposee != null ? proposee.name() : null, choisie.name(),
                confiancePriorite(r, proposee), emailAuteur, commentaire);

        r.setPriorite(choisie);
        r.setPrioriteValidee(true);
        reclamationRepository.save(r);

        // L'agent responsable est prévenu si quelqu'un d'autre change la priorité de son dossier.
        if (choisie != avant) {
            Utilisateur agent = agentDe(r);
            if (agent != null && !agent.getId().equals(auteur.getId())) {
                notificationService.creer(agent, "Priorité modifiée",
                        "La priorité de la réclamation " + r.getReference() + " est passée de "
                                + libelle(avant) + " à " + libelle(choisie) + ".", r.getId());
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("priorite", choisie.name());
        m.put("prioriteIA", proposee != null ? proposee.name() : null);
        m.put("decision", type.name());
        return m;
    }

    // ── 3. Catégorie (le citoyen s'est-il trompé ?) ───────────────────────

    /** Réclamations encore ouvertes dont la catégorie choisie ne correspond pas au texte, selon l'IA. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> categorieAVerifier() {
        return reclamationRepository.findByCategorieAVerifierTrueOrderByDateCreationDesc().stream()
                .filter(r -> r.getStatut() == null || !r.getStatut().estClos())
                .map(r -> {
                    Map<String, Object> m = resume(r);
                    m.put("categorieId", r.getCategorie() != null ? r.getCategorie().getIdCategorie() : null);
                    m.put("categorieSuggereeId", r.getCategorieSuggereeId());
                    m.put("categorieSuggeree", r.getCategorieSuggereeNom());
                    return m;
                })
                .toList();
    }

    /**
     * Garde (même catégorie) ou change la catégorie. Si elle change, la réclamation est réaffectée à un
     * agent du service de la nouvelle catégorie ; l'ancien agent et le citoyen sont prévenus.
     * Le contrôle d'accès (admin, ou agent responsable) est fait par le contrôleur appelant.
     */
    @Transactional
    public Map<String, Object> changerCategorie(Long reclamationId, String emailAuteur,
                                                Long nouvelleCategorieId, String commentaire) {
        Reclamation r = reclamation(reclamationId);
        if (r.getStatut() != null && r.getStatut().estClos()) {
            throw new IllegalArgumentException("Ce dossier est clos : sa catégorie ne peut plus être modifiée.");
        }
        if (nouvelleCategorieId == null) {
            throw new IllegalArgumentException("Choisissez une catégorie.");
        }
        CategorieReclamation nouvelle = categorieRepository.findById(nouvelleCategorieId)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie introuvable (id=" + nouvelleCategorieId + ")."));
        CategorieReclamation ancienne = r.getCategorie();
        boolean change = ancienne == null || !ancienne.getIdCategorie().equals(nouvelle.getIdCategorie());
        if (!change && !Boolean.TRUE.equals(r.getCategorieAVerifier())) {
            throw new IllegalArgumentException("La réclamation est déjà dans cette catégorie.");
        }

        // Proposition de l'IA = famille de la catégorie suggérée (null si l'IA n'avait rien signalé).
        // Proposition de l'IA = famille déduite du texte, seulement si elle avait signalé une erreur.
        String familleIA = r.getCategorieSuggereeIA() != null ? r.getFamilleSuggereeIA() : null;
        // Familles couvertes par la catégorie retenue (plusieurs possibles), sinon son nom.
        String familleRetenue = nouvelle.listeFamillesIA().isEmpty()
                ? nouvelle.getNom() : String.join(",", nouvelle.listeFamillesIA());
        String trace = "Catégorie : " + (ancienne != null ? ancienne.getNom() : "—") + " → " + nouvelle.getNom()
                + (commentaire == null || commentaire.isBlank() ? "" : ". " + commentaire.trim());
        Utilisateur auteur = enregistrer(r, change ? TypeFeedbackIA.CATEGORIE_CORRIGEE : TypeFeedbackIA.CATEGORIE_CONFIRMEE,
                familleIA, familleRetenue, null, emailAuteur, trace);

        r.setCategorieAVerifier(false);
        r.setCategorie(nouvelle);
        reclamationRepository.save(r);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("decision", change ? "CATEGORIE_CORRIGEE" : "CATEGORIE_CONFIRMEE");
        m.put("categorie", nouvelle.getNom());
        if (!change) return m;

        // Transfert vers le bon service.
        Utilisateur ancienAgent = agentDe(r);
        Utilisateur nouvelAgent = reclamationService.reaffecterSelonCategorie(r);
        String service = nouvelle.getServiceResponsable() != null ? nouvelle.getServiceResponsable().getNom() : nouvelle.getNom();
        if (ancienAgent != null && !ancienAgent.getId().equals(auteur.getId())
                && (nouvelAgent == null || !nouvelAgent.getId().equals(ancienAgent.getId()))) {
            notificationService.creer(ancienAgent, "Réclamation transférée",
                    "La réclamation " + r.getReference() + " a été reclassée en « " + nouvelle.getNom()
                            + " » et transférée au service " + service + ". Vous n'avez plus à la traiter.", r.getId());
        }
        if (r.getCitoyen() != null) {
            notificationService.creer(r.getCitoyen(), "Réclamation transmise au bon service",
                    "Votre réclamation \"" + r.getTitre() + "\" (réf. " + r.getReference()
                            + ") a été classée en « " + nouvelle.getNom() + " » et transmise au service " + service + ".",
                    r.getId());
        }
        // Si aucun agent n'a été trouvé, ReclamationService a déjà prévenu les administrateurs.
        m.put("nouvelAgent", nouvelAgent != null
                ? (Objects.toString(nouvelAgent.getPrenom(), "") + " " + Objects.toString(nouvelAgent.getNom(), "")).trim()
                : null);
        return m;
    }

    // ── Mesure de l'IA face aux humains ────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> statistiques() {
        // Périmètre : la décision humaine donne-t-elle raison à l'IA ?
        List<FeedbackIA> perimetre = feedbackRepository.findByTypeDecisionInOrderByDateDecisionAsc(DECISIONS_PERIMETRE);
        long accordPerimetre = perimetre.stream()
                .filter(f -> Objects.equals(f.getValeurIA(), f.getValeurHumaine())).count();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("aVerifier", perimetreAVerifier().size());
        p.put("municipalConfirmes", perimetre.stream()
                .filter(f -> f.getTypeDecision() == TypeFeedbackIA.PERIMETRE_MUNICIPAL_CONFIRME).count());
        p.put("horsPerimetreConfirmes", perimetre.stream()
                .filter(f -> f.getTypeDecision() == TypeFeedbackIA.HORS_PERIMETRE_CONFIRME).count());
        p.put("decisions", perimetre.size());
        p.put("accordIA", pourcentage(accordPerimetre, perimetre.size()));

        // Priorité : confirmée telle quelle, ou corrigée vers le haut / le bas ?
        List<FeedbackIA> priorite = feedbackRepository.findByTypeDecisionInOrderByDateDecisionAsc(DECISIONS_PRIORITE);
        long confirmees = 0, sousEstimees = 0, surEstimees = 0, aUnNiveauPres = 0;
        for (FeedbackIA f : priorite) {
            int ecart = rang(f.getValeurHumaine()) - rang(f.getValeurIA());
            if (ecart == 0) confirmees++;
            if (ecart > 0) sousEstimees++;   // l'humain a monté la priorité
            if (ecart < 0) surEstimees++;
            if (Math.abs(ecart) <= 1) aUnNiveauPres++;
        }
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("decisions", priorite.size());
        q.put("confirmees", confirmees);
        q.put("corrigees", priorite.size() - confirmees);
        q.put("sousEstimees", sousEstimees);
        q.put("surEstimees", surEstimees);
        q.put("precisionObservee", pourcentage(confirmees, priorite.size()));
        q.put("aUnNiveauPres", pourcentage(aUnNiveauPres, priorite.size()));

        // Catégorie : l'humain a-t-il suivi la suggestion de l'IA ?
        List<FeedbackIA> categorie = feedbackRepository.findByTypeDecisionInOrderByDateDecisionAsc(DECISIONS_CATEGORIE);
        List<FeedbackIA> signalees = categorie.stream().filter(f -> f.getValeurIA() != null).toList();
        // Suivie = la catégorie retenue couvre la famille suggérée par l'IA.
        long suivies = signalees.stream().filter(f -> f.getValeurHumaine() != null
                && Arrays.asList(f.getValeurHumaine().split(",")).contains(f.getValeurIA())).count();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("aVerifier", categorieAVerifier().size());
        c.put("decisions", categorie.size());
        c.put("corrigees", categorie.stream().filter(f -> f.getTypeDecision() == TypeFeedbackIA.CATEGORIE_CORRIGEE).count());
        c.put("gardees", categorie.stream().filter(f -> f.getTypeDecision() == TypeFeedbackIA.CATEGORIE_CONFIRMEE).count());
        // Erreurs vues par un humain mais pas par l'IA (correction sans signalement préalable).
        c.put("nonDetecteesParIA", categorie.size() - signalees.size());
        c.put("suggestionsSuivies", pourcentage(suivies, signalees.size()));

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("perimetre", p);
        s.put("priorite", q);
        s.put("categorie", c);
        return s;
    }

    /** dataset_v2 périmètre : texte + verdict humain (étiquette pour réentraîner is_municipal). */
    @Transactional(readOnly = true)
    public String exportPerimetreCsv() {
        StringBuilder sb = new StringBuilder("id_feedback,date_decision,decision,reclamation_id,texte,"
                + "probabilite_municipal_ia,verdict_ia,verdict_humain,is_municipal\n");
        for (FeedbackIA f : feedbackRepository.findByTypeDecisionInOrderByDateDecisionAsc(DECISIONS_PERIMETRE)) {
            Reclamation r = f.getReclamation();
            sb.append(f.getId()).append(',').append(date(f)).append(',').append(f.getTypeDecision()).append(',')
              .append(r != null ? r.getId() : "").append(',').append(csv(r != null ? texteDe(r) : "")).append(',')
              .append(f.getConfianceIA() != null ? f.getConfianceIA() : "").append(',')
              .append(nul(f.getValeurIA())).append(',').append(nul(f.getValeurHumaine())).append(',')
              .append(MUNICIPAL.equals(f.getValeurHumaine()) ? 1 : 0).append('\n');
        }
        return sb.toString();
    }

    /** dataset_v2 priorité : texte + priorité proposée + priorité décidée par un humain. */
    @Transactional(readOnly = true)
    public String exportPrioriteCsv() {
        StringBuilder sb = new StringBuilder("id_feedback,date_decision,decision,reclamation_id,texte,"
                + "priorite_ia,confiance_ia,alerte_critique_ia,priorite_humaine\n");
        for (FeedbackIA f : feedbackRepository.findByTypeDecisionInOrderByDateDecisionAsc(DECISIONS_PRIORITE)) {
            Reclamation r = f.getReclamation();
            sb.append(f.getId()).append(',').append(date(f)).append(',').append(f.getTypeDecision()).append(',')
              .append(r != null ? r.getId() : "").append(',').append(csv(r != null ? texteDe(r) : "")).append(',')
              .append(nul(f.getValeurIA())).append(',')
              .append(f.getConfianceIA() != null ? f.getConfianceIA() : "").append(',')
              .append(r != null && Boolean.TRUE.equals(r.getAlerteCritiqueIA()) ? 1 : 0).append(',')
              .append(nul(f.getValeurHumaine())).append('\n');
        }
        return sb.toString();
    }

    /** dataset_v2 catégorie : texte + famille suggérée par l'IA + famille retenue par un humain. */
    @Transactional(readOnly = true)
    public String exportCategorieCsv() {
        StringBuilder sb = new StringBuilder("id_feedback,date_decision,decision,reclamation_id,texte,"
                + "incident_ia,famille_suggeree_ia,famille_retenue,detail\n");
        for (FeedbackIA f : feedbackRepository.findByTypeDecisionInOrderByDateDecisionAsc(DECISIONS_CATEGORIE)) {
            Reclamation r = f.getReclamation();
            sb.append(f.getId()).append(',').append(date(f)).append(',').append(f.getTypeDecision()).append(',')
              .append(r != null ? r.getId() : "").append(',').append(csv(r != null ? texteDe(r) : "")).append(',')
              .append(r != null ? nul(r.getIncidentIA()) : "").append(',')
              .append(nul(f.getValeurIA())).append(',').append(nul(f.getValeurHumaine())).append(',')
              .append(csv(f.getCommentaire())).append('\n');
        }
        return sb.toString();
    }

    // ── Outils ─────────────────────────────────────────────────────────────

    private Utilisateur enregistrer(Reclamation r, TypeFeedbackIA type, String valeurIA, String valeurHumaine,
                                    Double confiance, String email, String commentaire) {
        Utilisateur auteur = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + email));
        FeedbackIA f = new FeedbackIA();
        f.setTypeDecision(type);
        f.setReclamation(r);
        f.setValeurIA(valeurIA);
        f.setValeurHumaine(valeurHumaine);
        f.setConfianceIA(confiance);
        f.setAuteur(auteur);
        f.setCommentaire(commentaire == null || commentaire.isBlank() ? null
                : commentaire.trim().substring(0, Math.min(500, commentaire.trim().length())));
        feedbackRepository.save(f);
        return auteur;
    }

    private Reclamation aVerifier(Long id) {
        Reclamation r = reclamation(id);
        if (!Boolean.TRUE.equals(r.getPerimetreAVerifier())) {
            throw new IllegalArgumentException("Le périmètre de cette réclamation a déjà été vérifié.");
        }
        return r;
    }

    private Reclamation reclamation(Long id) {
        return reclamationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Réclamation introuvable (id=" + id + ")."));
    }

    private static String verdictIA(Reclamation r) {
        if (r.getProbabiliteMunicipal() == null) return null;
        return r.getProbabiliteMunicipal() >= 0.5 ? MUNICIPAL : HORS_PERIMETRE;
    }

    /** Probabilité que l'IA donnait à la priorité proposée (lue dans la trace AnalyseIA NLP). */
    @SuppressWarnings("unchecked")
    private Double confiancePriorite(Reclamation r, Priorite proposee) {
        if (proposee == null) return null;
        List<AnalyseIA> a = analyseIARepository
                .findByReclamation_IdAndTypeAnalyseOrderByDateAnalyseDesc(r.getId(), TypeAnalyseIA.NLP);
        if (a.isEmpty() || a.get(0).getResultat() == null) return null;
        try {
            Map<String, Object> brut = JSON.readValue(a.get(0).getResultat(), Map.class);
            Object probas = brut.get("probabilites_priorite");
            if (probas instanceof Map<?, ?> m && m.get(proposee.name()) instanceof Number n) {
                return n.doubleValue();
            }
        } catch (Exception ignore) {
            // trace ancienne ou texte d'erreur : pas de confiance disponible
        }
        return null;
    }

    private Utilisateur agentDe(Reclamation r) {
        List<Affectation> a = affectationRepository.findByReclamationIdOrderByDateAffectationDesc(r.getId());
        return a.isEmpty() ? null : a.get(0).getAgent();
    }

    private Map<String, Object> resume(Reclamation r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("reference", r.getReference());
        m.put("titre", r.getTitre());
        m.put("description", r.getDescription());
        m.put("categorie", r.getCategorie() != null ? r.getCategorie().getNom() : null);
        m.put("statut", r.getStatut() != null ? r.getStatut().name() : null);
        m.put("dateCreation", r.getDateCreation() != null ? r.getDateCreation().toString() : null);
        m.put("probabiliteMunicipal", r.getProbabiliteMunicipal());
        m.put("verdictIA", verdictIA(r));
        m.put("incidentIA", r.getIncidentIA());
        m.put("citoyen", r.getCitoyen() != null
                ? (Objects.toString(r.getCitoyen().getPrenom(), "") + " " + Objects.toString(r.getCitoyen().getNom(), "")).trim()
                : null);
        return m;
    }

    private static int rang(String priorite) {
        if (priorite == null) return 0;
        return switch (priorite) {
            case "BASSE" -> 1;
            case "MOYENNE" -> 2;
            case "HAUTE" -> 3;
            case "CRITIQUE" -> 4;
            default -> 0;
        };
    }

    private static String libelle(Priorite p) {
        if (p == null) return "non définie";
        return switch (p) {
            case BASSE -> "Basse";
            case MOYENNE -> "Moyenne";
            case HAUTE -> "Haute";
            case CRITIQUE -> "Critique";
        };
    }

    private static Double pourcentage(long n, long total) {
        return total == 0 ? null : Math.round(1000.0 * n / total) / 10.0;
    }

    private static String texteDe(Reclamation r) {
        return (r.getTitre() == null ? "" : r.getTitre() + ". ") + Objects.toString(r.getDescription(), "");
    }

    private static String date(FeedbackIA f) {
        return f.getDateDecision() != null ? f.getDateDecision().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "";
    }

    private static String nul(String s) {
        return s == null ? "" : s;
    }

    private static String csv(String s) {
        return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\"";
    }
}
