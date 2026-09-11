package com.pfe.backend.controller;

import com.pfe.backend.entity.*;
import com.pfe.backend.entity.enums.Role;
import com.pfe.backend.entity.enums.StatutReclamation;
import com.pfe.backend.repository.*;
import com.pfe.backend.service.ReclamationService;
import com.pfe.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class AgentController {

    private final UtilisateurRepository     utilisateurRepository;
    private final AffectationRepository     affectationRepository;
    private final ReclamationRepository     reclamationRepository;
    private final HistoriqueStatutRepository historiqueRepository;
    private final InterventionRepository    interventionRepository;
    private final ReclamationService        reclamationService;
    private final NotificationService       notificationService;
    private final com.pfe.backend.repository.VoteRepository voteRepository;

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
        boolean assignee = affectationRepository.findAll().stream()
                .anyMatch(a -> a.getReclamation().getId().equals(id)
                        && a.getAgent().getId().equals(agent.getId()));
        if (!assignee) {
            throw new RuntimeException("Cette réclamation ne vous est pas assignée.");
        }

        String nouveauStatutStr = body.get("nouveauStatut");
        String commentaire      = body.getOrDefault("commentaire", "");

        StatutReclamation nouveauStatut;
        try {
            nouveauStatut = StatutReclamation.valueOf(nouveauStatutStr.toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("Statut invalide : " + nouveauStatutStr);
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
                    "Mise à jour de votre réclamation",
                    "Le statut de votre réclamation \"" + r.getTitre() + "\" (réf. "
                            + r.getReference() + ") est passé à : " + nouveauStatut + ".",
                    r.getId()
            );
        }

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
        return ResponseEntity.ok(stats);
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
}