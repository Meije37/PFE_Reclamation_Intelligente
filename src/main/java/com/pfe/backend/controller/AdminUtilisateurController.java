package com.pfe.backend.controller;

import com.pfe.backend.dto.UtilisateurCreateDTO;
import com.pfe.backend.dto.UtilisateurResponseDTO;
import com.pfe.backend.dto.UtilisateurUpdateDTO;
import com.pfe.backend.service.UtilisateurService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/utilisateurs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 🔥 pour Angular plus tard
public class AdminUtilisateurController {

    private final UtilisateurService utilisateurService;

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

    // ✅ READ BY ID
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
}