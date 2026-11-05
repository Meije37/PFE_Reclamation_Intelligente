package com.pfe.backend.controller;

import com.pfe.backend.dto.ServiceRequestDTO;
import com.pfe.backend.dto.ServiceResponseDTO;
import com.pfe.backend.service.ServiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.entity.enums.Role;
import com.pfe.backend.entity.enums.StatutReclamation;
import com.pfe.backend.repository.CategorieReclamationRepository;
import com.pfe.backend.repository.ReclamationRepository;
import com.pfe.backend.repository.UtilisateurRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/admin/services")
@RequiredArgsConstructor
public class AdminServiceController {

    private final ServiceService serviceService;
    private final UtilisateurRepository utilisateurRepository;
    private final ReclamationRepository reclamationRepository;
    private final CategorieReclamationRepository categorieRepository;

    /**
     * GET /api/admin/services/effectifs — pour chaque service : ses agents (actifs ou non) avec leur
     * charge (réclamations en cours) et les catégories qui lui envoient des réclamations.
     * Permet à l'admin de repérer un service sans agent actif : l'affectation automatique y est impossible.
     */
    @GetMapping("/effectifs")
    public ResponseEntity<List<Map<String, Object>>> getEffectifs() {
        List<Utilisateur> agents = utilisateurRepository.findByRole(Role.AGENT);
        var categories = categorieRepository.findAll();

        List<Map<String, Object>> resultat = serviceService.getAllServices().stream().map(s -> {
            List<Map<String, Object>> sesAgents = agents.stream()
                    .filter(a -> a.getService() != null && Objects.equals(a.getService().getId(), s.getId()))
                    .map(a -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", a.getId());
                        m.put("nom", (Objects.toString(a.getPrenom(), "") + " " + Objects.toString(a.getNom(), "")).trim());
                        m.put("email", a.getEmail());
                        m.put("actif", Boolean.TRUE.equals(a.getActif()));
                        Long enCours = reclamationRepository.countAffectationsParStatut(a.getId(), StatutReclamation.EN_COURS);
                        m.put("enCours", enCours != null ? enCours : 0L);
                        return m;
                    })
                    // Actifs d'abord, puis le moins chargé.
                    .sorted(Comparator.comparing((Map<String, Object> m) -> !(Boolean) m.get("actif"))
                            .thenComparing(m -> (Long) m.get("enCours")))
                    .toList();

            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("serviceId", s.getId());
            ligne.put("agents", sesAgents);
            ligne.put("agentsActifs", sesAgents.stream().filter(m -> (Boolean) m.get("actif")).count());
            ligne.put("reclamationsEnCours", sesAgents.stream().mapToLong(m -> (Long) m.get("enCours")).sum());
            ligne.put("categories", categories.stream()
                    .filter(c -> c.getServiceResponsable() != null
                            && Objects.equals(c.getServiceResponsable().getId(), s.getId()))
                    .map(c -> c.getNom()).toList());
            return ligne;
        }).toList();
        return ResponseEntity.ok(resultat);
    }

    @PostMapping
    public ResponseEntity<ServiceResponseDTO> createService(@Valid @RequestBody ServiceRequestDTO request) {
        ServiceResponseDTO response = serviceService.createService(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceResponseDTO> updateService(@PathVariable Long id,
                                                            @Valid @RequestBody ServiceRequestDTO request) {
        ServiceResponseDTO response = serviceService.updateService(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ServiceResponseDTO>> getAllServices() {
        return ResponseEntity.ok(serviceService.getAllServices());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceResponseDTO> getServiceById(@PathVariable Long id) {
        return ResponseEntity.ok(serviceService.getServiceById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteService(@PathVariable Long id) {
        serviceService.deleteService(id);
        return ResponseEntity.ok("Service supprimé avec succès");
    }
}