package com.pfe.backend.controller;

import com.pfe.backend.dto.ReclamationRequestDTO;
import com.pfe.backend.dto.ReclamationPubliqueDTO;
import com.pfe.backend.entity.HistoriqueStatut;
import com.pfe.backend.entity.Media;
import com.pfe.backend.entity.Reclamation;
import com.pfe.backend.entity.enums.StatutReclamation;
import com.pfe.backend.entity.enums.TypeMedia;
import com.pfe.backend.repository.MediaRepository;
import com.pfe.backend.service.ReclamationService;
import com.pfe.backend.service.StorageService;
import com.pfe.backend.service.VoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
//import com.fasterxml.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/citoyen")
@RequiredArgsConstructor // Cette annotation va générer le constructeur pour les 3 variables "final"
// Pas de @CrossOrigin ici : ce contrôleur est appelé par l'app mobile
// Flutter, qui n'est pas soumise au CORS (mécanisme de navigateur web
// uniquement). La config CORS globale (SecurityConfig) gère déjà le web.
public class CitoyenController {

    private final ReclamationService reclamationService;
    private final MediaRepository mediaRepository;
    private final StorageService storageService;
    private final VoteService voteService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats(Authentication authentication) {
        return ResponseEntity.ok(reclamationService.getStatsCitoyen(authentication.getName()));
    }


    @PostMapping(value = "/reclamations", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<Reclamation> deposerReclamation(
            @RequestPart("reclamation") String reclamationJson,
            @RequestPart(value = "image", required = false) MultipartFile file,
            Authentication authentication
    ) throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();
        ReclamationRequestDTO dto = objectMapper.readValue(reclamationJson, ReclamationRequestDTO.class);

        Reclamation nouvelle = reclamationService.creerReclamation(dto, authentication.getName());

        if (file != null && !file.isEmpty()) {
            String fileName = storageService.store(file);

            Media media = new Media();
            media.setNomFichier(fileName);
            media.setUrl("/uploads/" + fileName);
            media.setType(TypeMedia.PHOTO);
            media.setReclamation(nouvelle);
            mediaRepository.save(media);
        }

        return ResponseEntity.ok(nouvelle);
    }

    @GetMapping("/mes-reclamations")
    public ResponseEntity<Page<Reclamation>> getMesReclamations(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("dateCreation").descending());
        return ResponseEntity.ok(
                reclamationService.getReclamationsParEmailPage(authentication.getName(), pageable));
    }

    // ── Réclamations publiques (tous citoyens) + votes ──────────────────
    @GetMapping("/reclamations/publiques")
    public ResponseEntity<Page<ReclamationPubliqueDTO>> getReclamationsPubliques(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) Long categorieId,
            @RequestParam(required = false) StatutReclamation statut,
            // "recentes" (défaut) ou "votes"
            @RequestParam(defaultValue = "recentes") String tri) {
        PageRequest pageable = PageRequest.of(page, size);
        boolean trierParVotes = "votes".equalsIgnoreCase(tri);
        return ResponseEntity.ok(voteService.listerPubliques(
                authentication.getName(), zoneId, categorieId, statut, trierParVotes, pageable));
    }

    @PostMapping("/reclamations/{id}/vote")
    public ResponseEntity<VoteService.VoteResultat> voter(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(voteService.basculerVote(authentication.getName(), id));
    }

    @GetMapping("/reclamations/{id}")
    public ResponseEntity<Reclamation> getDétails(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(reclamationService.getReclamationDetaillee(id, authentication.getName()));
    }
    @PutMapping("/reclamations/{id}/annuler")
    public ResponseEntity<Void> annuler(@PathVariable Long id, Authentication authentication) {
        reclamationService.annulerReclamation(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
//    @PreAuthorize("hasRole('ADMIN')")
//    @GetMapping("/stats/{id}")
//    public ResponseEntity<Map<String, Long>> getStats(@PathVariable("id") Long id) { // Précisez ("id")
//        return ResponseEntity.ok(reclamationService.getCitoyenStats(id));
//    }

    // ✅ Correct — retourne List<HistoriqueStatut> directement
    @GetMapping("/reclamations/{id}/historique")
    public ResponseEntity<List<HistoriqueStatut>> getHistorique(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(
                reclamationService.getHistoriqueParCitoyen(id, authentication.getName())
        );
    }


}