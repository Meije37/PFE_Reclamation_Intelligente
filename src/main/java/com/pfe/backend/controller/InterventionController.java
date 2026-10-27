package com.pfe.backend.controller;

import com.pfe.backend.dto.AgentOptionDTO;
import com.pfe.backend.dto.InterventionCreateDTO;
import com.pfe.backend.dto.InterventionResponseDTO;
import com.pfe.backend.dto.InterventionStatutUpdateDTO;
import com.pfe.backend.dto.ServiceOptionDTO;
import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.entity.enums.Role;
import com.pfe.backend.repository.AffectationRepository;
import com.pfe.backend.repository.ServiceRepository;
import com.pfe.backend.repository.UtilisateurRepository;
import com.pfe.backend.service.InterventionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.pfe.backend.dto.InterventionTransfertDTO;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
public class InterventionController {

    private final InterventionService interventionService;
    private final ServiceRepository serviceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AffectationRepository affectationRepository;

    /**
     * Liste des services actifs, pour peupler le menu "service responsable"
     * lors de la planification d'une intervention. Distinct de
     * /api/admin/services (réservé ADMIN) — un agent doit pouvoir consulter
     * cette liste sans avoir les droits de gestion complète des services.
     */
    @GetMapping("/api/interventions/services-disponibles")
    public ResponseEntity<List<ServiceOptionDTO>> servicesDisponibles() {
        List<ServiceOptionDTO> services = serviceRepository.findByActifTrue().stream()
                .map(s -> new ServiceOptionDTO(s.getId(), s.getNom()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(services);
    }

    /**
     * Agents actifs d'un service donné, pour peupler le menu "agent
     * responsable" — remplace l'ancien choix "service seul" qui ne
     * désignait jamais un exécutant réel.
     */
    @GetMapping("/api/interventions/agents-disponibles")
    public ResponseEntity<List<AgentOptionDTO>> agentsDisponibles(@RequestParam Long serviceId) {
        List<AgentOptionDTO> agents = utilisateurRepository
                .findByServiceIdAndRoleAndActifTrue(serviceId, Role.AGENT)
                .stream()
                .map(a -> new AgentOptionDTO(a.getId(), a.getNom(), a.getPrenom()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(agents);
    }

    /**
     * Agent et service de l'affectation la plus récente d'une réclamation.
     * Sert à pré-remplir le formulaire de planification d'une intervention :
     * dans la plupart des cas, l'intervention est confiée à l'agent déjà
     * assigné au dossier. 204 si la réclamation n'a aucune affectation.
     */
    @GetMapping("/api/reclamations/{reclamationId}/affectation-courante")
    public ResponseEntity<Map<String, Object>> affectationCourante(
            @PathVariable Long reclamationId, Authentication authentication) {
        interventionService.verifierAccesReclamation(reclamationId, authentication.getName());
        return affectationRepository
                .findByReclamationIdOrderByDateAffectationDesc(reclamationId)
                .stream()
                .findFirst()
                .filter(a -> a.getAgent() != null)
                .map(a -> {
                    Utilisateur agent = a.getAgent();
                    Map<String, Object> reponse = new LinkedHashMap<>();
                    reponse.put("agentId", agent.getId());
                    reponse.put("serviceId", agent.getService() != null ? agent.getService().getId() : null);
                    return ResponseEntity.ok(reponse);
                })
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Interventions confiées à l'agent connecté, toutes réclamations
     * confondues — même si la réclamation ne lui est pas assignée en tant
     * qu'agent principal (Affectation).
     */
    @GetMapping("/api/interventions/mes-interventions")
    public ResponseEntity<List<InterventionResponseDTO>> mesInterventions(Authentication authentication) {
        return ResponseEntity.ok(interventionService.listerPourAgent(authentication.getName()));
    }

    @PostMapping("/api/reclamations/{reclamationId}/interventions")
    public ResponseEntity<InterventionResponseDTO> planifier(
            @PathVariable Long reclamationId,
            @Valid @RequestBody InterventionCreateDTO dto,
            Authentication authentication
    ) {
        InterventionResponseDTO cree = interventionService.planifier(reclamationId, authentication.getName(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(cree);
    }

    @GetMapping("/api/reclamations/{reclamationId}/interventions")
    public ResponseEntity<List<InterventionResponseDTO>> lister(
            @PathVariable Long reclamationId, Authentication authentication) {
        return ResponseEntity.ok(
                interventionService.listerPourReclamation(reclamationId, authentication.getName()));
    }

    @PatchMapping("/api/interventions/{interventionId}/statut")
    public ResponseEntity<InterventionResponseDTO> changerStatut(
            @PathVariable Long interventionId,
            @Valid @RequestBody InterventionStatutUpdateDTO dto,
            Authentication authentication
    ) {
        return ResponseEntity.ok(interventionService.changerStatut(interventionId, authentication.getName(), dto));
    }

    /** Transfère une intervention planifiée à un autre agent du service (agentId absent = automatique). */
    @PatchMapping("/api/interventions/{interventionId}/transfert")
    public ResponseEntity<InterventionResponseDTO> transferer(
            @PathVariable Long interventionId,
            @RequestBody InterventionTransfertDTO dto,
            Authentication authentication
    ) {
        return ResponseEntity.ok(interventionService.transferer(interventionId, authentication.getName(), dto));
    }
}