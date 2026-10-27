package com.pfe.backend.controller;

import com.pfe.backend.dto.ReclamationPubliqueDTO;
import com.pfe.backend.entity.enums.StatutReclamation;
import com.pfe.backend.repository.ReclamationRepository;
import com.pfe.backend.service.VoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consultation SANS compte (visiteur anonyme) — lecture seule.
 *
 * Sécurité : /api/public/** est déjà en permitAll() dans SecurityConfig.
 * Voter, commenter et déposer restent réservés aux citoyens connectés
 * (/api/citoyen/**).
 *
 * Aucune information sur l'auteur n'est renvoyée (ni nom, ni prénom,
 * ni e-mail, ni téléphone) et « aVote » vaut toujours false.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class ReclamationPubliqueController {

    private static final int TAILLE_MAX = 50;

    private final VoteService voteService;
    private final ReclamationRepository reclamationRepository;

    /** GET /api/public/reclamations?page=0&size=20&categorieId=&statut=&zoneId=&tri=recentes|votes */
    @GetMapping("/reclamations")
    public ResponseEntity<Page<ReclamationPubliqueDTO>> lister(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) Long categorieId,
            @RequestParam(required = false) StatutReclamation statut,
            @RequestParam(defaultValue = "recentes") String tri) {

        // Taille bornée : un visiteur anonyme ne doit pas pouvoir aspirer toute la base d'un coup.
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), TAILLE_MAX));
        boolean trierParVotes = "votes".equalsIgnoreCase(tri);
        return ResponseEntity.ok(voteService.listerPubliques(
                null, zoneId, categorieId, statut, trierParVotes, pageable));
    }

    /** GET /api/public/reclamations/{id} */
    @GetMapping("/reclamations/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(voteService.getDetailPublique(id, null));
    }

    /**
     * GET /api/public/stats
     * Chiffres globaux affichés sur l'accueil public (aucune donnée personnelle).
     * Les réclamations annulées par leur auteur ne sont pas comptées.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        long ouvertes = reclamationRepository.countByStatut(StatutReclamation.OUVERTE);
        long enCours  = reclamationRepository.countByStatut(StatutReclamation.EN_COURS);
        long resolues = reclamationRepository.countByStatut(StatutReclamation.RESOLUE)
                + reclamationRepository.countByStatut(StatutReclamation.FERMEE);
        long rejetees = reclamationRepository.countByStatut(StatutReclamation.REJETEE);
        long total    = ouvertes + enCours + resolues + rejetees;

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("total", total);
        dto.put("ouvertes", ouvertes);
        dto.put("enCours", enCours);
        dto.put("resolues", resolues);
        dto.put("tauxResolution", total == 0 ? 0 : Math.round(resolues * 100.0 / total));
        return ResponseEntity.ok(dto);
    }
}
