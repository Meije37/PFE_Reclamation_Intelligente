package com.pfe.backend.controller;

import com.pfe.backend.dto.CommentaireRequestDTO;
import com.pfe.backend.dto.CommentaireResponseDTO;
import com.pfe.backend.entity.Reclamation;
import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.repository.ReclamationRepository;
import com.pfe.backend.repository.UtilisateurRepository;
import com.pfe.backend.service.CommentaireService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Accessible à tout utilisateur authentifié — la visibilité (PUBLIC/INTERNE)
 * et le droit d'accès à une réclamation donnée sont vérifiés à l'intérieur
 * de CommentaireService, jamais en se fiant à ce qu'envoie le client.
 */
@RestController
@RequestMapping("/api/reclamations/{reclamationId}/commentaires")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class CommentaireController {

    private final CommentaireService commentaireService;
    private final ReclamationRepository reclamationRepository;
    private final UtilisateurRepository utilisateurRepository;

    private Utilisateur getUtilisateurConnecte(Authentication auth) {
        return utilisateurRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
    }

    private Reclamation getReclamation(Long id) {
        return reclamationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Réclamation introuvable"));
    }

    @GetMapping
    public ResponseEntity<?> lister(@PathVariable Long reclamationId, Authentication auth) {
        try {
            Utilisateur utilisateur = getUtilisateurConnecte(auth);
            Reclamation reclamation = getReclamation(reclamationId);
            List<CommentaireResponseDTO> commentaires =
                    commentaireService.lister(reclamation, utilisateur);
            return ResponseEntity.ok(commentaires);
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> ajouter(@PathVariable Long reclamationId,
                                     @RequestBody CommentaireRequestDTO dto,
                                     Authentication auth) {
        try {
            Utilisateur utilisateur = getUtilisateurConnecte(auth);
            Reclamation reclamation = getReclamation(reclamationId);
            CommentaireResponseDTO cree = commentaireService.ajouter(
                    reclamation, utilisateur, dto.getContenu(), dto.getVisibilite()
            );
            return ResponseEntity.status(201).body(cree);
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        }
    }
}