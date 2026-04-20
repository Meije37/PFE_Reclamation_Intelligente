package com.pfe.backend.controller;

import com.pfe.backend.dto.ReclamationRequestDTO;
import com.pfe.backend.entity.Media;
import com.pfe.backend.entity.Reclamation;
import com.pfe.backend.entity.enums.TypeMedia;
import com.pfe.backend.repository.MediaRepository;
import com.pfe.backend.service.ReclamationService;
import com.pfe.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/citoyen")
@RequiredArgsConstructor // Cette annotation va générer le constructeur pour les 3 variables "final"
@CrossOrigin(origins = "http://localhost:4200")
public class CitoyenController {

    private final ReclamationService reclamationService;
    private final MediaRepository mediaRepository;
    private final StorageService storageService;

@GetMapping("/stats")
public ResponseEntity<Map<String, Long>> getStats(Authentication authentication) {
    return ResponseEntity.ok(reclamationService.getStatsCitoyen(authentication.getName()));
}


    @PostMapping(value = "/reclamations", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<Reclamation> deposerReclamation(
            @RequestPart("reclamation") String reclamationJson,
            @RequestPart(value = "image", required = false) MultipartFile file,
            Authentication authentication // AJOUTER CECI pour l'ID du citoyen
    ) throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();
        ReclamationRequestDTO dto = objectMapper.readValue(reclamationJson, ReclamationRequestDTO.class);

        // Correction : Passer l'email pour lier le citoyen à la réclamation
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
    public ResponseEntity<List<Reclamation>> getMesReclamations(Authentication authentication) {
        return ResponseEntity.ok(reclamationService.getReclamationsParEmail(authentication.getName()));
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
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/stats/{id}")
    public ResponseEntity<Map<String, Long>> getStats(@PathVariable("id") Long id) { // Précisez ("id")
        return ResponseEntity.ok(reclamationService.getCitoyenStats(id));
    }



}