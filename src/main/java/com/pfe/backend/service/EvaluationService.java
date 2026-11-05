package com.pfe.backend.service;

import com.pfe.backend.entity.Affectation;
import com.pfe.backend.entity.Evaluation;
import com.pfe.backend.entity.Reclamation;
import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.entity.enums.Role;
import com.pfe.backend.entity.enums.StatutReclamation;
import com.pfe.backend.exception.AccesRefuseException;
import com.pfe.backend.exception.ResourceNotFoundException;
import com.pfe.backend.repository.AffectationRepository;
import com.pfe.backend.repository.EvaluationRepository;
import com.pfe.backend.repository.ReclamationRepository;
import com.pfe.backend.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Étape 8 — évaluation du traitement par le citoyen (note de 1 à 5 après résolution).
 *
 * Boucle de qualité : le citoyen note → l'agent voit sa moyenne → l'administrateur suit la
 * satisfaction globale et est alerté des avis négatifs (note ≤ 2), qu'il peut rouvrir.
 */
@Service
@RequiredArgsConstructor
public class EvaluationService {

    public static final int NOTE_MIN = 1;
    public static final int NOTE_MAX = 5;
    /** En dessous ou égal : avis négatif, l'administrateur est prévenu. */
    public static final int SEUIL_INSATISFAIT = 2;
    private static final int LONGUEUR_MAX_COMMENTAIRE = 500;

    private final EvaluationRepository evaluationRepository;
    private final ReclamationRepository reclamationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AffectationRepository affectationRepository;
    private final NotificationService notificationService;

    // ── Côté citoyen ───────────────────────────────────────────────────────

    /** Avis déjà donné sur sa réclamation, ou vide. */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getEvaluation(Long reclamationId, String emailCitoyen) {
        verifierProprietaire(reclamation(reclamationId), emailCitoyen);
        return evaluationRepository.findByReclamation_Id(reclamationId).map(EvaluationService::enMap);
    }

    @Transactional
    public Map<String, Object> evaluer(Long reclamationId, String emailCitoyen, Integer note, String commentaire) {
        Reclamation r = reclamation(reclamationId);
        Utilisateur citoyen = verifierProprietaire(r, emailCitoyen);

        if (r.getStatut() != StatutReclamation.RESOLUE) {
            throw new IllegalArgumentException("Vous pourrez donner votre avis quand la réclamation sera résolue.");
        }
        if (note == null || note < NOTE_MIN || note > NOTE_MAX) {
            throw new IllegalArgumentException("La note doit être comprise entre 1 et 5.");
        }
        if (evaluationRepository.existsByReclamation_Id(reclamationId)) {
            throw new IllegalArgumentException("Vous avez déjà donné votre avis sur cette réclamation.");
        }

        Evaluation e = new Evaluation();
        e.setReclamation(r);
        e.setCitoyen(citoyen);
        e.setAgent(agentResponsable(r));
        e.setNote(note);
        e.setCommentaire(nettoyer(commentaire));

        try {
            // saveAndFlush : si deux envois arrivent en même temps, la contrainte d'unicité
            // en base lève l'erreur ici, à l'intérieur du try.
            evaluationRepository.saveAndFlush(e);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Vous avez déjà donné votre avis sur cette réclamation.");
        }

        prevenir(e);
        return enMap(e);
    }

    // ── Côté agent ─────────────────────────────────────────────────────────

    /** {noteMoyenne, nombreAvis, derniersAvis} pour le tableau de bord de l'agent. */
    @Transactional(readOnly = true)
    public Map<String, Object> resumeAgent(Long agentId) {
        List<Evaluation> avis = evaluationRepository.findByAgent_Id(agentId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("noteMoyenne", moyenne(avis));
        m.put("nombreAvis", avis.size());
        // Pour que l'agent sache QUELLE réclamation a reçu quelle note (5 plus récents).
        m.put("derniersAvis", avis.stream()
                .sorted(Comparator.comparing(Evaluation::getDateEvaluation,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(EvaluationService::enMapAdmin)
                .toList());
        return m;
    }

    /** Avis du citoyen sur une réclamation (détail agent), ou vide. L'accès est vérifié par l'appelant. */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> avisDe(Long reclamationId) {
        return evaluationRepository.findByReclamation_Id(reclamationId).map(EvaluationService::enMap);
    }

    /** Note par réclamation, pour afficher les étoiles dans la liste de l'agent. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> notesPar(Collection<Long> reclamationIds) {
        if (reclamationIds.isEmpty()) return Map.of();
        return evaluationRepository.findByReclamation_IdIn(reclamationIds).stream()
                .collect(Collectors.toMap(e -> e.getReclamation().getId(), Evaluation::getNote, (a, b) -> a));
    }

    // ── Côté administrateur ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> statistiques() {
        List<Evaluation> toutes = evaluationRepository.findAllByOrderByDateEvaluationDesc();

        Map<Integer, Long> repartition = new LinkedHashMap<>();
        for (int n = NOTE_MAX; n >= NOTE_MIN; n--) repartition.put(n, 0L);
        toutes.forEach(e -> repartition.merge(e.getNote(), 1L, Long::sum));

        // Taux de réponse = réclamations évaluées / réclamations pouvant l'être.
        // Une réclamation évaluée peut avoir été clôturée ensuite : on l'ajoute au dénominateur.
        long resolues = reclamationRepository.countByStatut(StatutReclamation.RESOLUE);
        long evalueesPuisClosees = toutes.stream()
                .filter(e -> e.getReclamation().getStatut() != StatutReclamation.RESOLUE).count();
        long evaluables = resolues + evalueesPuisClosees;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombreAvis", toutes.size());
        m.put("noteMoyenne", moyenne(toutes));
        m.put("repartition", repartition);
        m.put("reclamationsEvaluables", evaluables);
        m.put("tauxReponse", evaluables == 0 ? null : Math.round(100.0 * toutes.size() / evaluables));
        m.put("satisfaits", toutes.stream().filter(e -> e.getNote() >= 4).count());
        m.put("insatisfaits", toutes.stream().filter(e -> e.getNote() <= SEUIL_INSATISFAIT).count());
        m.put("parAgent", parAgent(toutes));
        m.put("derniersAvis", toutes.stream().limit(6).map(EvaluationService::enMapAdmin).toList());
        return m;
    }

    private List<Map<String, Object>> parAgent(List<Evaluation> toutes) {
        Map<Long, List<Evaluation>> groupes = toutes.stream()
                .filter(e -> e.getAgent() != null)
                .collect(Collectors.groupingBy(e -> e.getAgent().getId(), LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> lignes = new ArrayList<>();
        groupes.forEach((id, avis) -> {
            Utilisateur a = avis.get(0).getAgent();
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("agentId", id);
            l.put("agent", ((a.getPrenom() == null ? "" : a.getPrenom()) + " "
                    + (a.getNom() == null ? "" : a.getNom())).trim());
            l.put("service", a.getService() != null ? a.getService().getNom() : null);
            l.put("nombreAvis", avis.size());
            l.put("noteMoyenne", moyenne(avis));
            lignes.add(l);
        });
        // Meilleure moyenne d'abord ; à égalité, celui qui a le plus d'avis.
        lignes.sort(Comparator
                .comparing((Map<String, Object> l) -> (Double) l.get("noteMoyenne")).reversed()
                .thenComparing(l -> (Integer) l.get("nombreAvis"), Comparator.reverseOrder()));
        return lignes;
    }

    // ── Outils ─────────────────────────────────────────────────────────────

    /** Agent du dossier ; pour un doublon fusionné, celui de la réclamation d'origine. */
    private Utilisateur agentResponsable(Reclamation r) {
        Utilisateur agent = dernierAgent(r);
        if (agent == null && r.getDoublonDe() != null) {
            agent = dernierAgent(r.getDoublonDe());
        }
        return agent;
    }

    private Utilisateur dernierAgent(Reclamation r) {
        List<Affectation> a = affectationRepository.findByReclamationIdOrderByDateAffectationDesc(r.getId());
        return a.isEmpty() ? null : a.get(0).getAgent();
    }

    private void prevenir(Evaluation e) {
        Reclamation r = e.getReclamation();
        String etoiles = e.getNote() + "/5";

        if (e.getAgent() != null) {
            notificationService.creer(e.getAgent(), "Nouvel avis citoyen",
                    "Le citoyen a noté le traitement de la réclamation " + r.getReference() + " : " + etoiles + ".",
                    r.getId());
        }
        if (e.getNote() <= SEUIL_INSATISFAIT) {
            String message = "Avis négatif (" + etoiles + ") sur la réclamation " + r.getReference()
                    + (e.getCommentaire() != null ? " : « " + e.getCommentaire() + " »" : ".")
                    + " Vérifiez si le problème est vraiment réglé.";
            for (Utilisateur admin : utilisateurRepository.findByRole(Role.ADMIN)) {
                notificationService.creer(admin, "Citoyen insatisfait", message, r.getId());
            }
        }
    }

    private Reclamation reclamation(Long id) {
        return reclamationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Réclamation introuvable (id=" + id + ")."));
    }

    private Utilisateur verifierProprietaire(Reclamation r, String email) {
        if (r.getCitoyen() == null || !r.getCitoyen().getEmail().equals(email)) {
            throw new AccesRefuseException("Seul l'auteur de la réclamation peut donner son avis.");
        }
        return r.getCitoyen();
    }

    private static String nettoyer(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > LONGUEUR_MAX_COMMENTAIRE ? t.substring(0, LONGUEUR_MAX_COMMENTAIRE) : t;
    }

    /** Moyenne arrondie à 0,1 près, ou null s'il n'y a aucun avis. */
    private static Double moyenne(List<Evaluation> avis) {
        if (avis.isEmpty()) return null;
        double m = avis.stream().mapToInt(Evaluation::getNote).average().orElse(0);
        return Math.round(m * 10) / 10.0;
    }

    private static Map<String, Object> enMap(Evaluation e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("note", e.getNote());
        m.put("commentaire", e.getCommentaire());
        m.put("dateEvaluation", e.getDateEvaluation() != null ? e.getDateEvaluation().toString() : null);
        return m;
    }

    private static Map<String, Object> enMapAdmin(Evaluation e) {
        Map<String, Object> m = enMap(e);
        m.put("reclamationId", e.getReclamation().getId());
        m.put("reference", e.getReclamation().getReference());
        m.put("titre", e.getReclamation().getTitre());
        // Prénom + initiale du nom (ex. « Mariem M. ») : assez pour reconnaître l'avis, sans tout exposer.
        Utilisateur c = e.getCitoyen();
        if (c != null) {
            String prenom = c.getPrenom() == null ? "" : c.getPrenom().trim();
            String nom = c.getNom() == null || c.getNom().isBlank() ? "" : " " + c.getNom().trim().charAt(0) + ".";
            m.put("citoyen", (prenom + nom).trim());
        }
        return m;
    }
}
