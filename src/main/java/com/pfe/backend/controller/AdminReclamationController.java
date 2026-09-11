package com.pfe.backend.controller;

import com.pfe.backend.dto.ChangerStatutRequestDTO;
import com.pfe.backend.entity.Affectation;
import com.pfe.backend.entity.HistoriqueStatut;
import com.pfe.backend.entity.Reclamation;
import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.entity.enums.Priorite;
import com.pfe.backend.entity.enums.Role;
import com.pfe.backend.entity.enums.StatutReclamation;
import com.pfe.backend.repository.AffectationRepository;
import com.pfe.backend.repository.HistoriqueStatutRepository;
import com.pfe.backend.repository.UtilisateurRepository;
import com.pfe.backend.repository.VoteRepository;
import com.pfe.backend.service.ReclamationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.Map;

// ============================================================
//  AdminReclamationController.java — VERSION FINALE PROPRE
//  Chemin : src/main/java/com/pfe/backend/controller/
//  Mapping : /api/admin/reclamations/**
//  Sécurité (SecurityConfig) : hasRole("ADMIN") — global
//  Aucun @PreAuthorize nécessaire ici : la règle globale suffit.
//
//  CONTIENT toutes les actions ADMIN sur les réclamations :
//
//  ✅ GET  /api/admin/reclamations
//          → Toutes les réclamations (ton original)
//
//  ✅ GET  /api/admin/reclamations/par-statut?statut=EN_COURS
//          → Filtre par StatutReclamation
//
//  ✅ GET  /api/admin/reclamations/par-priorite?priorite=CRITIQUE
//          → Filtre par Priorite
//
//  ✅ GET  /api/admin/reclamations/urgentes
//          → IA : triées par scoreUrgence décroissant
//
//  ✅ GET  /api/admin/reclamations/citoyen/{id}/stats
//          → Stats d'un citoyen par son ID
//          → Migrée depuis CitoyenController (était mal placée)
//
//  ZERO doublon avec CitoyenController :
//  CitoyenController  → /api/citoyen/** → identifie par email (Authentication)
//  AdminReclamation   → /api/admin/**   → identifie par ID dans l'URL
// ============================================================

@RestController
@RequestMapping("/api/admin/reclamations")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class AdminReclamationController {

    private final ReclamationService reclamationService;
    private final HistoriqueStatutRepository historiqueRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AffectationRepository affectationRepository;
    private final VoteRepository voteRepository;

    // ── Remplissage du compteur de votes (lecture seule, purement informatif) ──
    // Le champ Reclamation.nombreVotes n'est pas en base (voir @Transient) :
    // on le remplit ici juste avant de renvoyer la réponse JSON à l'admin.
    private void remplirNombreVotes(List<Reclamation> reclamations) {
        List<Long> ids = reclamations.stream().map(Reclamation::getId).toList();
        if (ids.isEmpty()) return;
        Map<Long, Long> votesParReclamation = voteRepository.countByReclamationIds(ids).stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (Long) row[0], row -> (Long) row[1]));
        reclamations.forEach(r ->
                r.setNombreVotes(votesParReclamation.getOrDefault(r.getId(), 0L)));
    }

    // Variante fluide (retourne la liste reçue) pratique pour un retour direct
    // en une ligne : return ResponseEntity.ok(withNombreVotes(liste));
    private List<Reclamation> withNombreVotes(List<Reclamation> reclamations) {
        remplirNombreVotes(reclamations);
        return reclamations;
    }

    private void remplirNombreVotes(Reclamation reclamation) {
        reclamation.setNombreVotes(voteRepository.countByReclamation_Id(reclamation.getId()));
    }

    private Reclamation withNombreVotes(Reclamation reclamation) {
        remplirNombreVotes(reclamation);
        return reclamation;
    }


    @GetMapping
    public ResponseEntity<List<Reclamation>> getAll() {
        List<Reclamation> reclamations = reclamationService.getAllReclamations();
        remplirNombreVotes(reclamations);
        return ResponseEntity.ok(reclamations);
    }


    @GetMapping("/par-statut")
    public ResponseEntity<List<Reclamation>> getParStatut(@RequestParam String statut) {
        StatutReclamation statutEnum;
        try {
            statutEnum = StatutReclamation.valueOf(statut.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                    "Statut invalide : '" + statut + "'. " +
                            "Valeurs : OUVERTE, EN_COURS, RESOLUE, REJETEE, FERMEE, ANNULEE"
            );
        }
        return ResponseEntity.ok(withNombreVotes(reclamationService.getReclamationsParStatut(statutEnum)));
    }
    @GetMapping("/par-priorite")
    public ResponseEntity<List<Reclamation>> getParPriorite(@RequestParam String priorite) {
        Priorite prioriteEnum;
        try {
            prioriteEnum = Priorite.valueOf(priorite.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                    "Priorité invalide : '" + priorite + "'. " +
                            "Valeurs : BASSE, MOYENNE, HAUTE, CRITIQUE"
            );
        }
        return ResponseEntity.ok(withNombreVotes(reclamationService.getReclamationsParPriorite(prioriteEnum)));
    }


    @GetMapping("/urgentes")
    public ResponseEntity<List<Reclamation>> getUrgentes() {
        return ResponseEntity.ok(withNombreVotes(reclamationService.getReclamationsUrgentes()));
    }


    @GetMapping("/citoyen/{id}/stats")
    public ResponseEntity<Map<String, Long>> getStatsCitoyen(@PathVariable Long id) {
        return ResponseEntity.ok(reclamationService.getCitoyenStats(id));
    }
    // PUT /api/reclamations/statut/{id}
    @PutMapping("/statut/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<Reclamation> changerStatut(
            @PathVariable Long id,
            @RequestBody ChangerStatutRequestDTO dto,
            Authentication authentication
    ) {
        // ← ajouter ce log
        System.out.println("USER: " + authentication.getName());
        System.out.println("ROLES: " + authentication.getAuthorities());

        Reclamation reclamation = reclamationService.changerStatut(id, dto);
        return ResponseEntity.ok(reclamation);
    }
    // POST /api/reclamations/assigner/{reclamationId}/agent/{agentId}
    @PostMapping("/assigner/{reclamationId}/agent/{agentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Affectation> assignerAgent(
            @PathVariable Long reclamationId,
            @PathVariable Long agentId
    ) {
        Affectation affectation = reclamationService.assignerAgent(reclamationId, agentId);
        return ResponseEntity.ok(affectation);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Reclamation> getById(@PathVariable Long id) {
        Reclamation reclamation = reclamationService.getAllReclamations()
                .stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Réclamation introuvable"));
        return ResponseEntity.ok(withNombreVotes(reclamation));
    }

    // 🆕 NOUVEAU — Historique des statuts d'une réclamation
    @GetMapping("/{id}/historique")
    public ResponseEntity<List<HistoriqueStatut>> getHistorique(@PathVariable Long id) {
        return ResponseEntity.ok(
                historiqueRepository.findByReclamationIdOrderByDateChangementDesc(id)
        );
    }

    @GetMapping("/agents")
    public ResponseEntity<List<java.util.Map<String, Object>>> getAgents() {
        List<java.util.Map<String, Object>> agents = utilisateurRepository.findAll()
                .stream()
                .filter(u -> u.getRole() == Role.AGENT && u.getActif())
                .map(u -> {
                    java.util.Map<String, Object> agent = new java.util.LinkedHashMap<>();
                    agent.put("id",        u.getId());
                    agent.put("nom",       u.getNom());
                    agent.put("prenom",    u.getPrenom());
                    agent.put("email",     u.getEmail());
                    agent.put("telephone", u.getTelephone());
                    agent.put("actif",     u.getActif());
                    return agent;
                })
                .toList();
        return ResponseEntity.ok(agents);
    }
    // GET /api/admin/reclamations/{id}/agent-assigne
    @GetMapping("/{id}/agent-assigne")
    public ResponseEntity<Map<String, Object>> getAgentAssigne(@PathVariable Long id) {
        // Chercher la dernière affectation manuelle de cette réclamation
        List<Affectation> affectations = affectationRepository.findAll() // Utilise la variable minuscule
                .stream()
                .filter(a -> a.getReclamation().getId().equals(id))
                .sorted((a, b) -> b.getDateAffectation().compareTo(a.getDateAffectation()))
                .toList();

        if (affectations.isEmpty()) {
            return ResponseEntity.ok(null);
        }

        Utilisateur agent = affectations.get(0).getAgent();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id",     agent.getId());
        result.put("nom",    agent.getNom());
        result.put("prenom", agent.getPrenom());
        result.put("email",  agent.getEmail());
        return ResponseEntity.ok(result);
    }
}