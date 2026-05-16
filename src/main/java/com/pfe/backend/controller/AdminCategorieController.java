package com.pfe.backend.controller;

import com.pfe.backend.entity.CategorieReclamation;
import com.pfe.backend.repository.CategorieReclamationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class AdminCategorieController {

    private final CategorieReclamationRepository categorieRepository;

    // GET /api/admin/categories
    @GetMapping
    public ResponseEntity<List<CategorieReclamation>> getAll() {
        return ResponseEntity.ok(categorieRepository.findAll());
    }

    // POST /api/admin/categories
    @PostMapping
    public ResponseEntity<CategorieReclamation> create(
            @RequestBody CategorieReclamation categorie) {
        categorie.setIdCategorie(null); // sécurité : forcer auto-génération
        return new ResponseEntity<>(categorieRepository.save(categorie), HttpStatus.CREATED);
    }

    // PUT /api/admin/categories/{id}
    @PutMapping("/{id}")
    public ResponseEntity<CategorieReclamation> update(
            @PathVariable Long id,
            @RequestBody CategorieReclamation categorie) {
        if (!categorieRepository.existsById(id)) {
            throw new RuntimeException("Catégorie introuvable avec l'id : " + id);
        }
        categorie.setIdCategorie(id);
        return ResponseEntity.ok(categorieRepository.save(categorie));
    }

    // DELETE /api/admin/categories/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        if (!categorieRepository.existsById(id)) {
            throw new RuntimeException("Catégorie introuvable avec l'id : " + id);
        }
        categorieRepository.deleteById(id);
        return ResponseEntity.ok("Catégorie supprimée avec succès");
    }
}