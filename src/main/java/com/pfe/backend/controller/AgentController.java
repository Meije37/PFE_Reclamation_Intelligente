package com.pfe.backend.controller;

import com.pfe.backend.entity.*;
import com.pfe.backend.entity.enums.Role;
import com.pfe.backend.entity.enums.StatutReclamation;
import com.pfe.backend.repository.*;
import com.pfe.backend.service.ReclamationService;
import com.pfe.backend.service.NotificationService;
import com.pfe.backend.service.TableauDeBordService;
import com.pfe.backend.service.RapportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final UtilisateurRepository     utilisateurRepository;
    private final AffectationRepository     affectationRepository;
    private final ReclamationRepository     reclamationRepository;
    private final HistoriqueStatutRepository historiqueRepository;
    private final InterventionRepository    interventionRepository;
    private final ReclamationService        reclamationService;
    private final NotificationService       notificationService;
    private final com.pfe.backend.repository.VoteRepository voteRepository;
    private final com.pfe.backend.service.DoublonIAService doublonIAService;
    private final com.pfe.backend.service.EvaluationService evaluationService;
    private final TableauDeBordService tableauDeBordService;
    private final RapportService rapportService;
    private final com.pfe.backend.service.SupervisionIAService supervisionIAService;

    /**
     * Un agent a accès à une réclamation soit parce qu'il en est l'agent
     * principal (Affectation), soit parce qu'une intervention lui a été
     * confiée dessus (même s'il appartient à un autre service que celui
     * assigné à l'origine — cf. cas "Voirie" qui confie une intervention
     * au service "Eau et Assainissement").
     */
    private boolean aAccesReclamation(Long reclamationId, Long agentId) {
        boolean viaAffectation = affectationRepository.findAll().stream()
                .anyMatch(a -> a.getReclamation().getId().equals(reclamationId)
                        && a.getAgent().getId().equals(agentId));
        if (viaAffectation) return true;

        return interventionRepository.findByAgentIdOrderByDateDebutDesc(agentId).stream()
                .anyMatch(i -> i.getReclamation().getId().equals(reclamationId));
    }

    //  récupérer l'agent connecté ----
    private Utilisateur getAgent(Authentication auth) {
        String email = auth.getName();
        return utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Agent introuvable"));
    }

    // ================================================================
    // GET /api/agent/mes-reclamations
    // Toutes les réclamations assignées à cet agent
    // ================================================================
    @GetMapping("/mes-reclamations")
    public ResponseEntity<List<Map<String, Object>>> getMesReclamations(
            Authentication auth) {

        Utilisateur agent = getAgent(auth);

        // Réclamations où l'agent est le responsable principal (Affectation)
        List<Affectation> mesAffectations = affectationRepository.findAll().stream()
                .filter(a -> a.getAgent().getId().equals(agent.getId()))
                .toList();

        Set<Long> idsViaAffectation = mesAffectations.stream()
                .map(a -> a.getReclamation().getId())
                .collect(java.util.stream.Collectors.toSet());

        // Pour retrouver rapidement l'Affectation d'une réclamation donnée
        // (mode + date), utilisé plus bas lors de la construction du DTO
        Map<Long, Affectation> affectationParReclamation = mesAffectations.stream()
                .collect(java.util.stream.Collectors.toMap(
                        a -> a.getReclamation().getId(), a -> a,
                        (a1, a2) -> a1)); // en cas de doublon, garder la première

        // + réclamations où l'agent a simplement une intervention confiée
        // (ex: agent d'un autre service, appelé en renfort sur ce dossier)
        Set<Long> idsViaIntervention = interventionRepository.findByAgentIdOrderByDateDebutDesc(agent.getId())
                .stream()
                .map(i -> i.getReclamation().getId())
                .collect(java.util.stream.Collectors.toSet());

        Set<Long> tousLesIds = new LinkedHashSet<>(idsViaAffectation);
        tousLesIds.addAll(idsViaIntervention);

        // Compteur de votes citoyens (purement informatif, une seule requête
        // groupée plutôt qu'une par réclamation affichée).
        Map<Long, Long> votesParReclamation = tousLesIds.isEmpty()
                ? Map.of()
                : voteRepository.countByReclamationIds(new java.util.ArrayList<>(tousLesIds)).stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (Long) row[0], row -> (Long) row[1]));

        // Étape 8 : note du citoyen (1 à 5) sur les réclamations déjà évaluées.
        Map<Long, Integer> notes = evaluationService.notesPar(tousLesIds);

        List<Map<String, Object>> result = tousLesIds.stream()
                .map(reclamationRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(r -> {
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("id",            r.getId());
                    dto.put("reference",     r.getReference());
                    dto.put("titre",         r.getTitre());
                    dto.put("description",   r.getDescription());
                    dto.put("statut",        r.getStatut().name());
                    dto.put("priorite",      r.getPriorite().name());
                    dto.put("scoreUrgence",  r.getScoreUrgence());
                    dto.put("dateCreation",  r.getDateCreation().toString());
                    dto.put("viaIntervention", !idsViaAffectation.contains(r.getId()));
                    dto.put("nombreVotes", votesParReclamation.getOrDefault(r.getId(), 0L));
                    dto.put("noteCitoyen", notes.get(r.getId())); // null = pas encore d'avis
                    // Mode et date d'affectation (absents si l'agent n'a accès
                    // qu'via une Intervention, sans Affectation principale)
                    Affectation affectation = affectationParReclamation.get(r.getId());
                    if (affectation != null) {
                        dto.put("modeAffectation", affectation.getModeAffectation().name());
                        dto.put("dateAffectation", affectation.getDateAffectation().toString());
                    }
                    // Catégorie
                    if (r.getCategorie() != null) {
                        dto.put("categorieNom", r.getCategorie().getNom());
                    }
                    // Localisation
                    if (r.getLocalisation() != null) {
                        Map<String, Object> loc = new LinkedHashMap<>();
                        loc.put("adresse",   r.getLocalisation().getAdresse());
                        loc.put("quartier",  r.getLocalisation().getQuartier());
                        loc.put("ville",     r.getLocalisation().getVille());
                        loc.put("latitude",  r.getLocalisation().getLatitude());
                        loc.put("longitude", r.getLocalisation().getLongitude());
                        dto.put("localisation", loc);
                    }
                    // Citoyen
                    if (r.getCitoyen() != null) {
                        Map<String, Object> cit = new LinkedHashMap<>();
                        cit.put("nom",       r.getCitoyen().getNom());
                        cit.put("prenom",    r.getCitoyen().getPrenom());
                        cit.put("email",     r.getCitoyen().getEmail());
                        cit.put("telephone", r.getCitoyen().getTelephone());
                        dto.put("citoyen", cit);
                    }
                    return dto;
                })
                .sorted((a, b) -> {
                    String sA = (String) a.get("statut");
                    String sB = (String) b.get("statut");
                    if ("EN_COURS".equals(sA) && !"EN_COURS".equals(sB)) return -1;
                    if (!"EN_COURS".equals(sA) && "EN_COURS".equals(sB)) return 1;
                    return ((String) b.get("dateCreation"))
                            .compareTo((String) a.get("dateCreation"));
                })
                .toList();

        return ResponseEntity.ok(result);
    }

    // ================================================================
    // GET /api/agent/mes-reclamations/{id}
    // Détail d'une réclamation assignée à cet agent
    // ================================================================
    @GetMapping("/mes-reclamations/{id}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true) // photos chargées à la demande
    public ResponseEntity<Map<String, Object>> getDetail(
            @PathVariable Long id, Authentication auth) {

        Utilisateur agent = getAgent(auth);

        Reclamation r = reclamationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Réclamation introuvable"));

        // Vérifier que cette réclamation est bien assignée à cet agent,
        // OU qu'une intervention lui a été confiée dessus
        boolean assignee = aAccesReclamation(id, agent.getId());

        if (!assignee) {
            throw new RuntimeException("Cette réclamation ne vous est pas assignée.");
        }

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id",           r.getId());
        dto.put("reference",    r.getReference());
        dto.put("titre",        r.getTitre());
        dto.put("description",  r.getDescription());
        dto.put("statut",       r.getStatut().name());
        dto.put("priorite",     r.getPriorite().name());
        dto.put("scoreUrgence", r.getScoreUrgence());
        dto.put("dateCreation", r.getDateCreation().toString());
        dto.put("estDoublon",   r.getEstDoublon());
        dto.put("nombreVotes",  voteRepository.countByReclamation_Id(r.getId()));
        // Vrai seulement pour le responsable ACTUEL (affectation la plus récente).
        // Un agent qui n'a qu'une intervention, ou un ancien responsable après
        // réassignation, voit le dossier mais ne peut pas changer son statut.
        dto.put("estResponsable", estResponsable(id, agent.getId()));
        // Analyse IA (proposition) : affichée à l'agent, qui peut confirmer ou corriger la priorité.
        dto.put("prioriteIA",           r.getPrioriteIA() != null ? r.getPrioriteIA().name() : null);
        dto.put("prioriteValidee",      Boolean.TRUE.equals(r.getPrioriteValidee()));
        dto.put("alerteCritiqueIA",     Boolean.TRUE.equals(r.getAlerteCritiqueIA()));
        dto.put("incidentIA",           r.getIncidentIA());
        dto.put("probabiliteMunicipal", r.getProbabiliteMunicipal());
        dto.put("perimetreAVerifier",   Boolean.TRUE.equals(r.getPerimetreAVerifier()));
        dto.put("confiancePrioriteIA",  r.getConfiancePrioriteIA());
        dto.put("categorieId",          r.getCategorie() != null ? r.getCategorie().getIdCategorie() : null);
        dto.put("categorieAVerifier",   Boolean.TRUE.equals(r.getCategorieAVerifier()));
        dto.put("categorieSuggereeId",  r.getCategorieSuggereeId());
        dto.put("categorieSuggereeNom", r.getCategorieSuggereeNom());

        if (r.getCategorie() != null) {
            dto.put("categorieNom", r.getCategorie().getNom());
        }
        if (r.getLocalisation() != null) {
            Map<String, Object> loc = new LinkedHashMap<>();
            loc.put("adresse",   r.getLocalisation().getAdresse());
            loc.put("quartier",  r.getLocalisation().getQuartier());
            loc.put("ville",     r.getLocalisation().getVille());
            loc.put("latitude",  r.getLocalisation().getLatitude());
            loc.put("longitude", r.getLocalisation().getLongitude());
            dto.put("localisation", loc);
        }
        if (r.getCitoyen() != null) {
            Map<String, Object> cit = new LinkedHashMap<>();
            cit.put("nom",       r.getCitoyen().getNom());
            cit.put("prenom",    r.getCitoyen().getPrenom());
            cit.put("email",     r.getCitoyen().getEmail());
            cit.put("telephone", r.getCitoyen().getTelephone());
            dto.put("citoyen", cit);
        }

        // Historique
        List<Map<String, Object>> historique = historiqueRepository
                .findAll().stream()
                .filter(h -> h.getReclamation().getId().equals(id))
                .map(h -> {
                    Map<String, Object> hDto = new LinkedHashMap<>();
                    hDto.put("ancienStatut",    h.getAncienStatut().name());
                    hDto.put("nouveauStatut",   h.getNouveauStatut().name());
                    hDto.put("dateChangement",  h.getDateChangement().toString());
                    hDto.put("commentaire",     h.getCommentaire());
                    return hDto;
                })
                .sorted((a, b) -> ((String) b.get("dateChangement"))
                        .compareTo((String) a.get("dateChangement")))
                .toList();

        dto.put("historique", historique);

        // Étape 8 : avis du citoyen {note, commentaire, dateEvaluation}, ou null.
        dto.put("evaluation", evaluationService.avisDe(id).orElse(null));

        // Photos du citoyen (affichées dans la page de détail de l'agent).
        dto.put("medias", r.getMedias() == null ? List.of() : r.getMedias().stream()
                .map(md -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("url", md.getUrl());
                    m.put("nomFichier", md.getNomFichier());
                    return m;
                })
                .toList());

        return ResponseEntity.ok(dto);
    }

    // ================================================================
    // PUT /api/agent/mes-reclamations/{id}/statut
    // Changer le statut — agent peut mettre EN_COURS, RESOLUE, REJETEE

    // ================================================================
    @PutMapping("/mes-reclamations/{id}/statut")
    public ResponseEntity<Map<String, Object>> changerStatut(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {

        Utilisateur agent = getAgent(auth);

        Reclamation r = reclamationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Réclamation introuvable"));

        // Vérifier assignation — délibérément restreint à l'agent PRINCIPAL
        // (Affectation) uniquement : changer le statut global du dossier
        // est une décision réservée au responsable principal, pas à
        // n'importe quel agent qui a juste une intervention dessus.
        boolean assignee = estResponsable(id, agent.getId());
        if (!assignee) {
            throw new RuntimeException("Cette réclamation ne vous est pas assignée.");
        }

        String nouveauStatutStr = body.get("nouveauStatut");
        String commentaire      = body.get("commentaire") == null ? "" : body.get("commentaire").trim();

        StatutReclamation nouveauStatut;
        try {
            nouveauStatut = StatutReclamation.valueOf(nouveauStatutStr.toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("Statut invalide : " + nouveauStatutStr);
        }

        // Un agent ne peut choisir que EN_COURS, RESOLUE ou REJETEE.
        if (nouveauStatut != StatutReclamation.EN_COURS
                && nouveauStatut != StatutReclamation.RESOLUE
                && nouveauStatut != StatutReclamation.REJETEE) {
            throw new RuntimeException("Un agent ne peut pas mettre ce statut : " + nouveauStatut);
        }

        // Un dossier clos (résolu, rejeté, fermé, annulé) ne se rouvre pas côté agent :
        // seul l'administrateur peut le faire.
        if (r.getStatut() != null && r.getStatut().estClos()) {
            throw new RuntimeException("Cette réclamation est déjà " + r.getStatut().getLibelle()
                    + " : seul un administrateur peut la rouvrir.");
        }

        // Le citoyen doit pouvoir comprendre pourquoi sa réclamation est refusée.
        if (nouveauStatut == StatutReclamation.REJETEE && commentaire.isEmpty()) {
            throw new RuntimeException("Un motif est obligatoire pour rejeter une réclamation.");
        }

        // Enregistrer historique
        HistoriqueStatut historique = new HistoriqueStatut();
        historique.setReclamation(r);
        historique.setAncienStatut(r.getStatut());
        historique.setNouveauStatut(nouveauStatut);
        historique.setDateChangement(LocalDateTime.now());
        historique.setCommentaire(commentaire.isEmpty()
                ? "Mis à jour par l'agent " + agent.getPrenom() + " " + agent.getNom()
                : commentaire);
        historiqueRepository.save(historique);

        r.setStatut(nouveauStatut);
        reclamationRepository.save(r);

        if (r.getCitoyen() != null) {
            notificationService.creer(
                    r.getCitoyen(),
                    com.pfe.backend.service.MessagesStatut.titre(nouveauStatut),
                    com.pfe.backend.service.MessagesStatut.message(r, nouveauStatut, commentaire),
                    r.getId()
            );
        }

        // Les doublons confirmés suivent la réclamation d'origine (résolue, rejetée ou fermée).
        doublonIAService.propagerStatut(r, nouveauStatut, commentaire);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message",      "Statut mis à jour avec succès.");
        response.put("nouveauStatut", nouveauStatut.name());
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // GET /api/agent/stats
    // Statistiques personnelles de l'agent connecté
    // ================================================================
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(Authentication auth) {

        Utilisateur agent = getAgent(auth);

        List<Affectation> affectations =affectationRepository.findByAgentId(agent.getId());
        long total    = affectations.size();
        long enCours  = affectations.stream()
                .filter(a -> a.getReclamation().getStatut() == StatutReclamation.EN_COURS)
                .count();
        long resolues = affectations.stream()
                .filter(a -> a.getReclamation().getStatut() == StatutReclamation.RESOLUE)
                .count();
        long rejetees = affectations.stream()
                .filter(a -> a.getReclamation().getStatut() == StatutReclamation.REJETEE)
                .count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total",         total);
        stats.put("enCours",       enCours);
        stats.put("resolues",      resolues);
        stats.put("rejetees",      rejetees);
        stats.put("agentNom",      agent.getNom());
        stats.put("agentPrenom",   agent.getPrenom());
        stats.put("serviceNom",    agent.getService() != null
                ? agent.getService().getNom() : null);
        // Étape 8 : satisfaction des citoyens sur les dossiers de l'agent.
        stats.putAll(evaluationService.resumeAgent(agent.getId()));
        return ResponseEntity.ok(stats);
    }
    // ================================================================
    // GET /api/agent/tableau-de-bord
    // Travail personnel de l'agent : à traiter, en cours, délai moyen,
    // réclamations urgentes (avec retard), répartition par quartier.
    // ================================================================
    @GetMapping("/tableau-de-bord")
    public ResponseEntity<Map<String, Object>> getTableauDeBord(Authentication auth) {
        return ResponseEntity.ok(tableauDeBordService.apercuAgent(getAgent(auth)));
    }

    // ================================================================
    // GET /api/agent/verifications-ia
    // Propositions de l'IA à confirmer sur les dossiers de l'agent :
    // priorités non encore confirmées et catégories à vérifier.
    // ================================================================
    @GetMapping("/verifications-ia")
    public ResponseEntity<Map<String, Object>> getVerificationsIA(Authentication auth) {
        return ResponseEntity.ok(tableauDeBordService.verificationsAgent(getAgent(auth)));
    }

    // ================================================================
    // GET /api/agent/rapports/synthese?du=…&au=…  → rapport personnel (JSON)
    // GET /api/agent/rapports/excel?du=…&au=…     → classeur .xlsx
    // Seulement les réclamations dont l'agent est le responsable actuel.
    // Sans dates : les 30 derniers jours.
    // ================================================================
    @GetMapping("/rapports/synthese")
    public ResponseEntity<?> rapportSynthese(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate au,
            Authentication auth) {
        java.time.LocalDate[] p = periodeRapport(du, au);
        if (p == null) return ResponseEntity.badRequest().body(Map.of("message", "La date de début doit précéder la date de fin."));
        return ResponseEntity.ok(rapportService.synthese(p[0], p[1], getAgent(auth)));
    }

    @GetMapping("/rapports/excel")
    public ResponseEntity<?> rapportExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate au,
            Authentication auth) throws java.io.IOException {
        java.time.LocalDate[] p = periodeRapport(du, au);
        if (p == null) return ResponseEntity.badRequest().body(Map.of("message", "La date de début doit précéder la date de fin."));
        byte[] fichier = rapportService.excel(p[0], p[1], getAgent(auth));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"mon_rapport_chikayti_" + p[0] + "_" + p[1] + ".xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(fichier);
    }

    /** Période par défaut : 30 derniers jours ; null si début > fin. */
    private static java.time.LocalDate[] periodeRapport(java.time.LocalDate du, java.time.LocalDate au) {
        java.time.LocalDate fin = au != null ? au : java.time.LocalDate.now();
        java.time.LocalDate debut = du != null ? du : fin.minusDays(29);
        return debut.isAfter(fin) ? null : new java.time.LocalDate[]{debut, fin};
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMonProfilComplet(Authentication auth) {
        Utilisateur agent = getAgent(auth);

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id",        agent.getId());
        dto.put("nom",       agent.getNom());
        dto.put("prenom",    agent.getPrenom());
        dto.put("email",     agent.getEmail());
        dto.put("telephone", agent.getTelephone());
        dto.put("role",      agent.getRole().name());

        return ResponseEntity.ok(dto);
    }

    // ================================================================
    // PUT /api/agent/mes-reclamations/{id}/priorite  { "priorite": "HAUTE", "commentaire": "…" }
    // L'agent responsable confirme ou corrige la priorité proposée par l'IA.
    // Chaque décision est un retour pour l'IA (FeedbackIA → dataset_v2 priorité).
    // ================================================================
    @PutMapping("/mes-reclamations/{id}/priorite")
    public ResponseEntity<?> validerPriorite(@PathVariable Long id,
                                             @RequestBody Map<String, String> body,
                                             Authentication auth) {
        Utilisateur agent = getAgent(auth);
        if (!estResponsable(id, agent.getId())) {
            return ResponseEntity.status(403).body(Map.of("message",
                    "Seul l'agent responsable du dossier peut modifier sa priorité."));
        }
        try {
            return ResponseEntity.ok(supervisionIAService.validerPriorite(id, auth.getName(),
                    body.get("priorite"), body.get("commentaire")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ================================================================
    // PUT /api/agent/mes-reclamations/{id}/categorie  { "categorieId": 3, "commentaire": "…" }
    // L'agent responsable reclasse une réclamation mal catégorisée : elle est transférée
    // au service de la nouvelle catégorie (il n'en est donc plus responsable ensuite).
    // ================================================================
    @PutMapping("/mes-reclamations/{id}/categorie")
    public ResponseEntity<?> changerCategorie(@PathVariable Long id,
                                              @RequestBody Map<String, Object> body,
                                              Authentication auth) {
        Utilisateur agent = getAgent(auth);
        if (!estResponsable(id, agent.getId())) {
            return ResponseEntity.status(403).body(Map.of("message",
                    "Seul l'agent responsable du dossier peut changer sa catégorie."));
        }
        Long categorieId = body.get("categorieId") instanceof Number n ? n.longValue() : null;
        Object commentaire = body.get("commentaire");
        try {
            return ResponseEntity.ok(supervisionIAService.changerCategorie(id, auth.getName(), categorieId,
                    commentaire != null ? commentaire.toString() : null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Responsable actuel = agent de l'affectation la plus récente de la réclamation. */
    private boolean estResponsable(Long reclamationId, Long agentId) {
        return affectationRepository.findByReclamationIdOrderByDateAffectationDesc(reclamationId)
                .stream()
                .findFirst()
                .map(a -> a.getAgent() != null && a.getAgent().getId().equals(agentId))
                .orElse(false);
    }
}