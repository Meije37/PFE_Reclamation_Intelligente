package com.pfe.backend.controller;

import com.pfe.backend.entity.CategorieReclamation;
import com.pfe.backend.entity.Service;
import com.pfe.backend.repository.CategorieReclamationRepository;
import com.pfe.backend.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class AdminCategorieController {

    private final CategorieReclamationRepository categorieRepository;
    private final ServiceRepository serviceRepository;

    private Map<String, Object> toDTO(CategorieReclamation c) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("idCategorie",       c.getIdCategorie());
        dto.put("nom",               c.getNom());
        dto.put("description",       c.getDescription());
        dto.put("prioriteParDefaut", c.getPrioriteParDefaut());
        if (c.getServiceResponsable() != null) {
            dto.put("serviceId",  c.getServiceResponsable().getId());
            dto.put("serviceNom", c.getServiceResponsable().getNom());
        } else {
            dto.put("serviceId",  null);
            dto.put("serviceNom", null);
        }
        return dto;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        return ResponseEntity.ok(
                categorieRepository.findAll().stream().map(this::toDTO).toList()
        );
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        CategorieReclamation cat = new CategorieReclamation();
        cat.setNom((String) body.get("nom"));
        cat.setDescription((String) body.get("description"));
        cat.setPrioriteParDefaut((Integer) body.get("prioriteParDefaut"));
        if (body.get("serviceId") != null) {
            Long serviceId = Long.valueOf(body.get("serviceId").toString());
            serviceRepository.findById(serviceId).ifPresent(cat::setServiceResponsable);
        }
        return new ResponseEntity<>(toDTO(categorieRepository.save(cat)), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        CategorieReclamation cat = categorieRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Catégorie introuvable : " + id));
        cat.setNom((String) body.get("nom"));
        cat.setDescription((String) body.get("description"));
        cat.setPrioriteParDefaut((Integer) body.get("prioriteParDefaut"));
        if (body.get("serviceId") != null) {
            Long serviceId = Long.valueOf(body.get("serviceId").toString());
            serviceRepository.findById(serviceId).ifPresent(cat::setServiceResponsable);
        } else {
            cat.setServiceResponsable(null);
        }
        return ResponseEntity.ok(toDTO(categorieRepository.save(cat)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        if (!categorieRepository.existsById(id))
            throw new RuntimeException("Catégorie introuvable : " + id);
        categorieRepository.deleteById(id);
        return ResponseEntity.ok("Catégorie supprimée avec succès");
    }

    @GetMapping("/{id}/service")
    public ResponseEntity<Map<String, Object>> getService(@PathVariable Long id) {
        CategorieReclamation cat = categorieRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Catégorie introuvable : " + id));
        return ResponseEntity.ok(toDTO(cat));
    }
}