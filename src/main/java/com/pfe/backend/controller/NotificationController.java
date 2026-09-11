package com.pfe.backend.controller;

import com.pfe.backend.dto.NotificationResponseDTO;
import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.repository.UtilisateurRepository;
import com.pfe.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Accessible à tout utilisateur authentifié (citoyen, agent, admin) —
 * chacun ne voit et ne modifie que SES propres notifications, filtrées
 * via l'utilisateur connecté (jamais d'ID passé par le client).
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class NotificationController {

    private final NotificationService notificationService;
    private final UtilisateurRepository utilisateurRepository;

    private Utilisateur getUtilisateurConnecte(Authentication auth) {
        String email = auth.getName();
        return utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponseDTO>> mesNotifications(Authentication auth) {
        Utilisateur utilisateur = getUtilisateurConnecte(auth);
        return ResponseEntity.ok(notificationService.listerPourUtilisateur(utilisateur));
    }

    @GetMapping("/non-lues/count")
    public ResponseEntity<Map<String, Long>> compterNonLues(Authentication auth) {
        Utilisateur utilisateur = getUtilisateurConnecte(auth);
        return ResponseEntity.ok(Map.of("count", notificationService.compterNonLues(utilisateur)));
    }

    @PatchMapping("/{id}/lu")
    public ResponseEntity<Void> marquerCommeLue(@PathVariable Long id, Authentication auth) {
        Utilisateur utilisateur = getUtilisateurConnecte(auth);
        notificationService.marquerCommeLue(id, utilisateur);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/lu-toutes")
    public ResponseEntity<Void> marquerToutesCommeLues(Authentication auth) {
        Utilisateur utilisateur = getUtilisateurConnecte(auth);
        notificationService.marquerToutesCommeLues(utilisateur);
        return ResponseEntity.noContent().build();
    }
}