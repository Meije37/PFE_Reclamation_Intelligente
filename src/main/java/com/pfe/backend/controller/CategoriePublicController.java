package com.pfe.backend.controller;

import com.pfe.backend.entity.CategorieReclamation;
import com.pfe.backend.repository.CategorieReclamationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// ============================================================
//  PRIORITE #1 — Catégories publiques
//  Chemin : src/main/java/com/pfe/backend/controller/
//  Fichier : CategoriePublicController.java
// ============================================================
//
//  POURQUOI CE FICHIER ?
//  Le formulaire Angular de création de réclamation a besoin
//  de charger la liste des catégories pour remplir le champ
//  categorieId. Sans cet endpoint, le formulaire est bloqué.
//
//  SECURITE :
//  La route /api/public/** n'est PAS dans SecurityConfig.
//  Il faut ajouter cette ligne dans SecurityConfig.java :
//
//      .requestMatchers("/api/public/**").permitAll()
//
//  Ajoute-la juste après la ligne "/api/auth/**" :
//
//      .requestMatchers("/api/auth/**").permitAll()
//      .requestMatchers("/api/public/**").permitAll()   <-- AJOUTER
//      .requestMatchers("/api/admin/**").hasRole("ADMIN")
//      ...
//
// ============================================================

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class CategoriePublicController {

    private final CategorieReclamationRepository categorieRepository;

    /**
     * GET /api/public/categories
     * Retourne toutes les catégories disponibles.
     * Accessible sans authentification (formulaire de création).
     */
    @GetMapping("/categories")
    public ResponseEntity<List<CategorieReclamation>> getAllCategories() {
        List<CategorieReclamation> categories = categorieRepository.findAll();
        return ResponseEntity.ok(categories);
    }
}