package com.pfe.backend.dto;

import lombok.Getter;
import lombok.Setter;

// ============================================================
//  PRIORITE #2 — DTO pour le changement de statut
//  Chemin : src/main/java/com/pfe/backend/dto/
//  Fichier : ChangerStatutRequestDTO.java
// ============================================================
//
//  Ce DTO reçoit le corps de la requête PUT pour changer
//  le statut d'une réclamation. Il inclut un commentaire
//  optionnel qui sera sauvegardé dans l'HistoriqueStatut.
//
// ============================================================

@Getter
@Setter
public class ChangerStatutRequestDTO {

    // Valeurs acceptées : OUVERTE, EN_COURS, RESOLUE, REJETEE, FERMEE, ANNULEE
    private String statut;

    // Commentaire optionnel (raison du changement, remarque de l'agent)
    private String commentaire;
}