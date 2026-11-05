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
public class AdminCategorieController {

    private final CategorieReclamationRepository categorieRepository;
    private final ServiceRepository serviceRepository;

    /** Familles reconnues par l'IA (voir FAMILLE_INCIDENT dans pfe-ai-service/app/moteur.py). */
    private static final java.util.Set<String> FAMILLES_IA =
            new java.util.HashSet<>(com.pfe.backend.entity.FamillesIA.TOUTES);

    /** Accepte une liste ["EAU_ASSAINISSEMENT", …] ou une chaîne « A,B » ; renvoie « A,B » ou null. */
    private static String famillesIA(Object valeur) {
        if (valeur == null) return null;
        java.util.List<String> brut = valeur instanceof java.util.Collection<?> c
                ? c.stream().map(String::valueOf).toList()
                : java.util.Arrays.asList(valeur.toString().split(","));
        java.util.List<String> familles = brut.stream()
                .map(f -> f.trim().toUpperCase()).filter(f -> !f.isEmpty()).distinct().toList();
        for (String f : familles) {
            if (!FAMILLES_IA.contains(f)) throw new RuntimeException("Famille IA inconnue : " + f);
        }
        return familles.isEmpty() ? null : String.join(",", familles);
    }

    private Map<String, Object> toDTO(CategorieReclamation c) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("idCategorie",       c.getIdCategorie());
        dto.put("nom",               c.getNom());
        dto.put("description",       c.getDescription());
        dto.put("prioriteParDefaut", c.getPrioriteParDefaut());
        dto.put("famillesIA",        c.listeFamillesIA());         // familles utilisées par l'IA
        dto.put("famillesIAManuelles", c.famillesIAManuelles());   // cochées à la main (peut être vide)
        dto.put("famillesIADeduites",  c.famillesIADeduites());    // déduites du nom du service
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
        cat.setFamillesIA(famillesIA(body.get("famillesIA")));
        cat.setFamilleIA(null); // ancienne colonne remplacée par famillesIA
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
        cat.setFamillesIA(famillesIA(body.get("famillesIA")));
        cat.setFamilleIA(null); // ancienne colonne remplacée par famillesIA
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