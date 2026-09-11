package com.pfe.backend.controller;

import com.pfe.backend.dto.AgentOptionDTO;
import com.pfe.backend.dto.InterventionCreateDTO;
import com.pfe.backend.dto.InterventionResponseDTO;
import com.pfe.backend.dto.InterventionStatutUpdateDTO;
import com.pfe.backend.dto.ServiceOptionDTO;
import com.pfe.backend.entity.enums.Role;
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

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
public class InterventionController {

    private final InterventionService interventionService;
    private final ServiceRepository serviceRepository;
    private final UtilisateurRepository utilisateurRepository;

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
    public ResponseEntity<List<InterventionResponseDTO>> lister(@PathVariable Long reclamationId) {
        return ResponseEntity.ok(interventionService.listerPourReclamation(reclamationId));
    }

    @PatchMapping("/api/interventions/{interventionId}/statut")
    public ResponseEntity<InterventionResponseDTO> changerStatut(
            @PathVariable Long interventionId,
            @Valid @RequestBody InterventionStatutUpdateDTO dto,
            Authentication authentication
    ) {
        return ResponseEntity.ok(interventionService.changerStatut(interventionId, authentication.getName(), dto));
    }
}