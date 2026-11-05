package com.pfe.backend.service;

import com.pfe.backend.entity.Affectation;
import com.pfe.backend.entity.AnalyseIA;
import com.pfe.backend.entity.FeedbackIA;
import com.pfe.backend.entity.HistoriqueStatut;
import com.pfe.backend.entity.Reclamation;
import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.entity.Vote;
import com.pfe.backend.entity.enums.StatutReclamation;
import com.pfe.backend.entity.enums.TypeAnalyseIA;
import com.pfe.backend.entity.enums.TypeFeedbackIA;
import com.pfe.backend.repository.AffectationRepository;
import com.pfe.backend.repository.AnalyseIARepository;
import com.pfe.backend.repository.HistoriqueStatutRepository;
import com.pfe.backend.repository.VoteRepository;
import com.pfe.backend.repository.FeedbackIARepository;
import com.pfe.backend.repository.ReclamationRepository;
import com.pfe.backend.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Human-in-the-Loop pour les doublons (étape 7).
 *
 * L'IA PROPOSE (estDoublon = true + trace AnalyseIA de type DETECTION_DOUBLON) ;
 * un administrateur DÉCIDE (confirmer / rejeter) ; chaque décision est enregistrée
 * dans FeedbackIA et exportable en CSV (dataset_v2) pour mesurer et améliorer l'IA.
 */
@Service
@RequiredArgsConstructor
public class DoublonIAService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<TypeFeedbackIA> DECISIONS_DOUBLON =
            List.of(TypeFeedbackIA.DOUBLON_CONFIRME, TypeFeedbackIA.DOUBLON_REJETE);

    private final ReclamationRepository reclamationRepository;
    private final AnalyseIARepository analyseIARepository;
    private final FeedbackIARepository feedbackRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final HistoriqueStatutRepository historiqueRepository;
    private final AffectationRepository affectationRepository;
    private final VoteRepository voteRepository;
    private final NotificationService notificationService;

    // ── Liste « Doublons à vérifier » ──────────────────────────────────────

    /** Réclamations signalées par l'IA et pas encore traitées par un humain, avec leurs candidats. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aVerifier() {
        List<Map<String, Object>> resultat = new ArrayList<>();
        for (Reclamation r : reclamationRepository.findByEstDoublonTrueOrderByDateCreationDesc()) {
            if (r.getDoublonDe() != null
                    || feedbackRepository.existsByReclamation_IdAndTypeDecisionIn(r.getId(), DECISIONS_DOUBLON)) {
                continue; // déjà décidé
            }
            List<Map<String, Object>> candidats = new ArrayList<>();
            for (Map<String, Object> d : propositionsIA(r.getId())) {
                Long idOrigine = d.get("id") instanceof Number n ? n.longValue() : null;
                if (idOrigine == null) continue;
                reclamationRepository.findById(idOrigine).ifPresent(o -> {
                    Map<String, Object> c = resume(o);
                    c.put("distanceM", d.get("distance_m"));
                    c.put("similarite", d.get("similarite"));
                    candidats.add(c);
                });
            }
            Map<String, Object> ligne = resume(r);
            ligne.put("candidats", candidats);
            resultat.add(ligne);
        }
        return resultat;
    }

    // ── Décisions ──────────────────────────────────────────────────────────

    @Transactional
    public void confirmer(Long reclamationId, Long origineId, String emailAdmin, String commentaire) {
        Reclamation r = reclamation(reclamationId);
        Reclamation origine = reclamation(origineId);
        if (r.getId().equals(origine.getId())) {
            throw new IllegalArgumentException("Une réclamation ne peut pas être son propre doublon.");
        }
        r.setEstDoublon(true);
        r.setDoublonDe(origine);
        enregistrer(TypeFeedbackIA.DOUBLON_CONFIRME, r, origine, emailAdmin, commentaire);

        // 1. Le doublon est FERMÉ : un seul dossier (l'origine) continue d'être traité, par un seul agent.
        if (r.getStatut() == null || !r.getStatut().estClos()) {
            historiser(r, StatutReclamation.FERMEE, "Même problème que la réclamation " + origine.getReference()
                    + ", déjà prise en charge. Le traitement se poursuit sur cette réclamation.");
            r.setStatut(StatutReclamation.FERMEE);
        }
        reclamationRepository.save(r);

        // 2. L'agent du doublon est prévenu qu'il n'a pas à intervenir (son dossier apparaît fermé).
        Utilisateur agentOrigine = agentDe(origine);
        for (Affectation a : affectationRepository.findByReclamationIdOrderByDateAffectationDesc(r.getId())) {
            Utilisateur agent = a.getAgent();
            if (agent != null && (agentOrigine == null || !agent.getId().equals(agentOrigine.getId()))) {
                notificationService.creer(agent,
                        "Réclamation fusionnée : pas d'intervention",
                        r.getReference() + " est un doublon de " + origine.getReference()
                                + (agentOrigine != null ? ", suivie par " + agentOrigine.getPrenom() + " "
                                + agentOrigine.getNom() : "") + ". Inutile de vous déplacer.",
                        r.getId());
            }
        }

        // 3. Le citoyen du doublon est informé, et son signalement compte comme un soutien de l'origine.
        Utilisateur citoyen = r.getCitoyen();
        if (citoyen != null) {
            notificationService.creer(citoyen,
                    "Votre signalement a été regroupé",
                    "Votre réclamation " + r.getReference() + " concerne le même problème que la réclamation "
                            + origine.getReference() + ", déjà en cours de traitement. Vous serez prévenu de sa résolution.",
                    r.getId());
            boolean auteurOrigine = origine.getCitoyen() != null && origine.getCitoyen().getId().equals(citoyen.getId());
            if (!auteurOrigine && voteRepository.findByReclamation_IdAndCitoyen_Id(origine.getId(), citoyen.getId()).isEmpty()) {
                Vote v = new Vote();
                v.setReclamation(origine);
                v.setCitoyen(citoyen);
                v.setValeur(1);
                v.setDateVote(LocalDateTime.now());
                voteRepository.save(v);
            }
        }
    }

    /**
     * Quand la réclamation d'ORIGINE est résolue, rejetée ou fermée, ses doublons confirmés suivent
     * le même statut et leurs citoyens sont prévenus. À appeler après chaque changement de statut
     * (ReclamationService.changerStatut et AgentController).
     */
    @Transactional
    public void propagerStatut(Reclamation origine, StatutReclamation nouveau, String motif) {
        if (nouveau != StatutReclamation.RESOLUE && nouveau != StatutReclamation.REJETEE
                && nouveau != StatutReclamation.FERMEE) {
            return;
        }
        for (Reclamation d : reclamationRepository.findByDoublonDe_Id(origine.getId())) {
            if (d.getStatut() == nouveau) continue;
            String texte = switch (nouveau) {
                case RESOLUE -> "Problème résolu (traité avec la réclamation " + origine.getReference() + ").";
                case REJETEE -> (motif == null || motif.isBlank() ? "Réclamation rejetée" : motif.trim())
                        + " (décision prise sur la réclamation " + origine.getReference() + ").";
                default -> "Dossier clôturé avec la réclamation " + origine.getReference() + ".";
            };
            historiser(d, nouveau, texte);
            d.setStatut(nouveau);
            reclamationRepository.save(d);
            if (d.getCitoyen() != null) {
                notificationService.creer(d.getCitoyen(), MessagesStatut.titre(nouveau),
                        MessagesStatut.message(d, nouveau, texte), d.getId());
            }
        }
    }

    private void historiser(Reclamation r, StatutReclamation nouveau, String commentaire) {
        HistoriqueStatut h = new HistoriqueStatut();
        h.setReclamation(r);
        h.setAncienStatut(r.getStatut());
        h.setNouveauStatut(nouveau);
        h.setDateChangement(LocalDateTime.now());
        h.setCommentaire(commentaire);
        historiqueRepository.save(h);
    }

    private Utilisateur agentDe(Reclamation r) {
        List<Affectation> a = affectationRepository.findByReclamationIdOrderByDateAffectationDesc(r.getId());
        return a.isEmpty() ? null : a.get(0).getAgent();
    }

    @Transactional
    public void rejeter(Long reclamationId, String emailAdmin, String commentaire) {
        Reclamation r = reclamation(reclamationId);
        // On garde la meilleure proposition de l'IA comme « origine » : c'est un exemple négatif précieux.
        Reclamation proposee = propositionsIA(r.getId()).stream()
                .map(d -> d.get("id") instanceof Number n ? n.longValue() : null)
                .filter(id -> id != null)
                .findFirst()
                .flatMap(reclamationRepository::findById)
                .orElse(null);
        r.setEstDoublon(false);
        r.setDoublonDe(null);
        reclamationRepository.save(r);
        enregistrer(TypeFeedbackIA.DOUBLON_REJETE, r, proposee, emailAdmin, commentaire);
    }

    // ── Mesure de l'IA et export ───────────────────────────────────────────

    /** Précision OBSERVÉE de l'IA sur les doublons : part des propositions confirmées par un humain. */
    @Transactional(readOnly = true)
    public Map<String, Object> statistiques() {
        long confirmes = feedbackRepository.countByTypeDecision(TypeFeedbackIA.DOUBLON_CONFIRME);
        long rejetes = feedbackRepository.countByTypeDecision(TypeFeedbackIA.DOUBLON_REJETE);
        long decides = confirmes + rejetes;
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("aVerifier", aVerifier().size());
        s.put("confirmes", confirmes);
        s.put("rejetes", rejetes);
        s.put("precisionObservee", decides == 0 ? null : Math.round(1000.0 * confirmes / decides) / 10.0);
        return s;
    }

    /**
     * dataset_v2 : une ligne par décision humaine (paire de réclamations + mesures de l'IA + verdict).
     * Format lisible par pandas : pfe-ai-service/training/calibrer_doublons.py pourra s'en servir
     * pour recalibrer les seuils sur des données RÉELLES.
     */
    @Transactional(readOnly = true)
    public String exportCsv() {
        DateTimeFormatter f = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        StringBuilder sb = new StringBuilder(
                "id_feedback,date_decision,decision,doublon,reclamation_id,texte_a,lat_a,lon_a," +
                "origine_id,texte_b,lat_b,lon_b,distance_m,similarite_ia\n");
        for (FeedbackIA fb : feedbackRepository.findByTypeDecisionInOrderByDateDecisionAsc(DECISIONS_DOUBLON)) {
            Reclamation a = fb.getReclamation();
            Reclamation b = fb.getReclamationOrigine();
            sb.append(fb.getId()).append(',')
              .append(fb.getDateDecision() != null ? fb.getDateDecision().format(f) : "").append(',')
              .append(fb.getTypeDecision()).append(',')
              .append(fb.getTypeDecision() == TypeFeedbackIA.DOUBLON_CONFIRME ? 1 : 0).append(',')
              .append(a != null ? a.getId() : "").append(',')
              .append(csv(a != null ? texteDe(a) : "")).append(',')
              .append(a != null && a.getLocalisation() != null ? a.getLocalisation().getLatitude() : "").append(',')
              .append(a != null && a.getLocalisation() != null ? a.getLocalisation().getLongitude() : "").append(',')
              .append(b != null ? b.getId() : "").append(',')
              .append(csv(b != null ? texteDe(b) : "")).append(',')
              .append(b != null && b.getLocalisation() != null ? b.getLocalisation().getLatitude() : "").append(',')
              .append(b != null && b.getLocalisation() != null ? b.getLocalisation().getLongitude() : "").append(',')
              .append(fb.getDistanceM() != null ? fb.getDistanceM() : "").append(',')
              .append(fb.getSimilarite() != null ? fb.getSimilarite() : "")
              .append('\n');
        }
        return sb.toString();
    }

    // ── Outils ─────────────────────────────────────────────────────────────

    /** Doublons proposés par l'IA pour cette réclamation (dernière analyse DETECTION_DOUBLON). */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> propositionsIA(Long reclamationId) {
        List<AnalyseIA> analyses = analyseIARepository
                .findByReclamation_IdAndTypeAnalyseOrderByDateAnalyseDesc(reclamationId, TypeAnalyseIA.DETECTION_DOUBLON);
        if (analyses.isEmpty() || analyses.get(0).getResultat() == null) {
            return List.of();
        }
        try {
            Map<String, Object> r = JSON.readValue(analyses.get(0).getResultat(), Map.class);
            Object liste = r.get("doublons_potentiels");
            return liste instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        } catch (Exception e) {
            return List.of(); // trace ancienne ou illisible : rien à proposer
        }
    }

    private void enregistrer(TypeFeedbackIA type, Reclamation r, Reclamation origine,
                             String emailAdmin, String commentaire) {
        FeedbackIA fb = new FeedbackIA();
        fb.setTypeDecision(type);
        fb.setReclamation(r);
        fb.setReclamationOrigine(origine);
        fb.setCommentaire(commentaire);
        fb.setAuteur(utilisateurRepository.findByEmail(emailAdmin).orElse(null));
        if (origine != null) {
            for (Map<String, Object> d : propositionsIA(r.getId())) {
                if (d.get("id") instanceof Number n && n.longValue() == origine.getId()) {
                    fb.setDistanceM(d.get("distance_m") instanceof Number x ? x.doubleValue() : null);
                    fb.setSimilarite(d.get("similarite") instanceof Number x ? x.doubleValue() : null);
                }
            }
        }
        feedbackRepository.save(fb);
    }

    private Reclamation reclamation(Long id) {
        return reclamationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Réclamation introuvable : " + id));
    }

    private static Map<String, Object> resume(Reclamation r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("reference", r.getReference());
        m.put("titre", r.getTitre());
        m.put("description", r.getDescription());
        m.put("dateCreation", r.getDateCreation() != null ? r.getDateCreation().toString() : null);
        m.put("statut", r.getStatut() != null ? r.getStatut().name() : null);
        m.put("priorite", r.getPriorite() != null ? r.getPriorite().name() : null);
        m.put("categorie", r.getCategorie() != null ? r.getCategorie().getNom() : null);
        if (r.getLocalisation() != null) {
            m.put("latitude", r.getLocalisation().getLatitude());
            m.put("longitude", r.getLocalisation().getLongitude());
            m.put("adresse", r.getLocalisation().getAdresse());
        }
        return m;
    }

    private static String texteDe(Reclamation r) {
        return (r.getTitre() == null ? "" : r.getTitre() + ". ") + (r.getDescription() == null ? "" : r.getDescription());
    }

    /** Champ CSV entre guillemets (les textes contiennent des virgules et des retours à la ligne). */
    private static String csv(String s) {
        return "\"" + s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
    }
}
