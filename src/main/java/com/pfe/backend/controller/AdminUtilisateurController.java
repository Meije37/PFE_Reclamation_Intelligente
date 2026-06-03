package com.pfe.backend.controller;

import com.pfe.backend.dto.UtilisateurCreateDTO;
import com.pfe.backend.dto.UtilisateurResponseDTO;
import com.pfe.backend.dto.UtilisateurUpdateDTO;
import com.pfe.backend.service.UtilisateurService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.entity.enums.Role;
import com.pfe.backend.repository.ServiceRepository;
import com.pfe.backend.repository.UtilisateurRepository;
import java.util.Map;
import java.util.LinkedHashMap;

import java.util.List;

@RestController
@RequestMapping("/api/admin/utilisateurs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") //
public class AdminUtilisateurController {

    private final UtilisateurService utilisateurService;
    private final UtilisateurRepository utilisateurRepository;
    private final ServiceRepository     serviceRepository;

    // ✅ CREATE
    @PostMapping
    public ResponseEntity<UtilisateurResponseDTO> createUtilisateur(@RequestBody UtilisateurCreateDTO dto) {
        UtilisateurResponseDTO response = utilisateurService.createUtilisateur(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ✅ READ ALL
    @GetMapping
    public ResponseEntity<List<UtilisateurResponseDTO>> getAllUtilisateurs() {
        List<UtilisateurResponseDTO> utilisateurs = utilisateurService.getAllUtilisateurs();
        return ResponseEntity.ok(utilisateurs);
    }

    //  READ BY ID
    @GetMapping("/{id}")
    public ResponseEntity<UtilisateurResponseDTO> getUtilisateurById(@PathVariable Long id) {
        UtilisateurResponseDTO utilisateur = utilisateurService.getUtilisateurById(id);
        return ResponseEntity.ok(utilisateur);
    }

    // ✅ UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<UtilisateurResponseDTO> updateUtilisateur(
            @PathVariable Long id,
            @RequestBody UtilisateurUpdateDTO dto
    ) {
        UtilisateurResponseDTO updated = utilisateurService.updateUtilisateur(id, dto);
        return ResponseEntity.ok(updated);
    }

    // ✅ DESACTIVER
    @PutMapping("/{id}/desactiver")
    public ResponseEntity<UtilisateurResponseDTO> desactiverUtilisateur(@PathVariable Long id) {
        UtilisateurResponseDTO utilisateur = utilisateurService.desactiverUtilisateur(id);
        return ResponseEntity.ok(utilisateur);
    }

    // ✅ ACTIVER
    @PutMapping("/{id}/activer")
    public ResponseEntity<UtilisateurResponseDTO> activerUtilisateur(@PathVariable Long id) {
        UtilisateurResponseDTO utilisateur = utilisateurService.activerUtilisateur(id);
        return ResponseEntity.ok(utilisateur);
    }

    // ✅ DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUtilisateur(@PathVariable Long id) {
        utilisateurService.deleteUtilisateur(id);
        return ResponseEntity.ok("Utilisateur supprimé avec succès");
    }

// Tous les agents avec leur service rattaché
    @GetMapping("/agents")
    public ResponseEntity<List<Map<String, Object>>> getAllAgents() {
        List<Map<String, Object>> agents = utilisateurRepository.findAll()
                .stream()
                .filter(u -> u.getRole() == Role.AGENT)
                .map(u -> {
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("id",         u.getId());
                    dto.put("nom",        u.getNom());
                    dto.put("prenom",     u.getPrenom());
                    dto.put("email",      u.getEmail());
                    dto.put("telephone",  u.getTelephone());
                    dto.put("actif",      u.getActif());
                    dto.put("serviceId",  u.getService() != null ? u.getService().getId()  : null);
                    dto.put("serviceNom", u.getService() != null ? u.getService().getNom() : null);
                    return dto;
                })
                .toList();
        return ResponseEntity.ok(agents);
    }


// Affecter un agent à un service
    @PutMapping("/{id}/affecter-service/{serviceId}")
    public ResponseEntity<Map<String, Object>> affecterService(
            @PathVariable Long id,
            @PathVariable Long serviceId) {

        Utilisateur agent = utilisateurRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable : " + id));

        if (agent.getRole() != Role.AGENT) {
            throw new RuntimeException("Seul un AGENT peut être affecté à un service.");
        }

        com.pfe.backend.entity.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Service introuvable : " + serviceId));

        agent.setService(service);
        utilisateurRepository.save(agent);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message",    "Agent affecté au service avec succès.");
        response.put("agentId",    agent.getId());
        response.put("agentNom",   agent.getPrenom() + " " + agent.getNom());
        response.put("serviceId",  service.getId());
        response.put("serviceNom", service.getNom());
        return ResponseEntity.ok(response);
    }


// Retirer un agent de son service
    @PutMapping("/{id}/retirer-service")
    public ResponseEntity<Map<String, Object>> retirerService(@PathVariable Long id) {

        Utilisateur agent = utilisateurRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable : " + id));

        agent.setService(null);
        utilisateurRepository.save(agent);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message",  "Agent retiré de son service.");
        response.put("agentId",  agent.getId());
        response.put("agentNom", agent.getPrenom() + " " + agent.getNom());
        return ResponseEntity.ok(response);
    }


    //  OBTENIR LE PROFIL DE L'UTILISATEUR CONNECTÉ
    @GetMapping("/me")
    public ResponseEntity<UtilisateurResponseDTO> getMonProfil() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String emailConnecte = authentication.getName();


        Utilisateur utilisateur = utilisateurRepository.findByEmail(emailConnecte)
                .orElseThrow(() -> new RuntimeException("Utilisateur connecté introuvable"));

        // 3. Retourner le DTO correspondant
        UtilisateurResponseDTO response = UtilisateurResponseDTO.builder()
                .id(utilisateur.getId())
                .nom(utilisateur.getNom())
                .prenom(utilisateur.getPrenom())
                .email(utilisateur.getEmail())
                .telephone(utilisateur.getTelephone())
                .actif(utilisateur.getActif())
                .role(utilisateur.getRole())
                .build();

        return ResponseEntity.ok(response);
    }
}