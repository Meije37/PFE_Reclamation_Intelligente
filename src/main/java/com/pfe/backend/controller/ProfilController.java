package com.pfe.backend.controller;

import com.pfe.backend.dto.ChangerMotDePasseDTO;
import com.pfe.backend.dto.ProfilUpdateDTO;
import com.pfe.backend.dto.UtilisateurResponseDTO;
import com.pfe.backend.service.ProfilService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Gestion du profil de l'utilisateur connecté, quel que soit son rôle
 * (CITOYEN, AGENT, ADMIN) — d'où l'absence de préfixe /citoyen, /agent ou
 * /admin : ce contrôleur agit toujours sur Authentication.getName(), jamais
 * sur un ID passé en paramètre.
 */
@RestController
@RequestMapping("/api/profil")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProfilController {

    private final ProfilService profilService;

    @GetMapping
    public ResponseEntity<UtilisateurResponseDTO> monProfil(Authentication authentication) {
        return ResponseEntity.ok(profilService.monProfil(authentication.getName()));
    }

    @PutMapping
    public ResponseEntity<UtilisateurResponseDTO> modifier(
            @Valid @RequestBody ProfilUpdateDTO dto,
            Authentication authentication
    ) {
        return ResponseEntity.ok(profilService.modifierProfil(authentication.getName(), dto));
    }

    @PutMapping("/mot-de-passe")
    public ResponseEntity<Void> changerMotDePasse(
            @Valid @RequestBody ChangerMotDePasseDTO dto,
            Authentication authentication
    ) {
        profilService.changerMotDePasse(authentication.getName(), dto);
        return ResponseEntity.noContent().build();
    }
}