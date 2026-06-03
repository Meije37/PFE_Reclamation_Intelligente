package com.pfe.backend.controller;

import com.pfe.backend.entity.*;
import com.pfe.backend.entity.enums.Role;
import com.pfe.backend.entity.enums.StatutReclamation;
import com.pfe.backend.repository.*;
import com.pfe.backend.service.ReclamationService;
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
    private final ReclamationService        reclamationService;

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

        List<Map<String, Object>> result = affectationRepository.findAll()
                .stream()
                .filter(a -> a.getAgent().getId().equals(agent.getId()))
                .map(a -> {
                    Reclamation r = a.getReclamation();
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("id",            r.getId());
                    dto.put("reference",     r.getReference());
                    dto.put("titre",         r.getTitre());
                    dto.put("description",   r.getDescription());
                    dto.put("statut",        r.getStatut().name());
                    dto.put("priorite",      r.getPriorite().name());
                    dto.put("scoreUrgence",  r.getScoreUrgence());
                    dto.put("dateCreation",  r.getDateCreation().toString());
                    dto.put("modeAffectation", a.getModeAffectation().name());
                    dto.put("dateAffectation", a.getDateAffectation().toString());
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
                    // Trier : EN_COURS en premier, puis par date
                    String sA = (String) a.get("statut");
                    String sB = (String) b.get("statut");
                    if ("EN_COURS".equals(sA) && !"EN_COURS".equals(sB)) return -1;
                    if (!"EN_COURS".equals(sA) && "EN_COURS".equals(sB)) return 1;
                    return ((String) b.get("dateAffectation"))
                            .compareTo((String) a.get("dateAffectation"));
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

        // Vérifier que cette réclamation est bien assignée à cet agent
        boolean assignee = affectationRepository.findAll().stream()
                .anyMatch(a -> a.getReclamation().getId().equals(id)
                        && a.getAgent().getId().equals(agent.getId()));

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

        // Vérifier assignation
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