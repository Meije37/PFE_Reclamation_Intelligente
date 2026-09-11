package com.pfe.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Représentation d'une réclamation pour la liste PUBLIQUE (visible par tous
 * les citoyens, pas seulement son auteur).
 *
 * Volontairement allégé par rapport à l'entité Reclamation complète : ne
 * contient PAS l'email ni le téléphone du citoyen auteur (uniquement son
 * prénom), pour ne pas exposer les coordonnées personnelles d'un citoyen
 * aux autres utilisateurs de l'app.
 */
@Getter
@Setter
@AllArgsConstructor
public class ReclamationPubliqueDTO {
    private Long id;
    private String reference;
    private String titre;
    private String description;
    private String statut;
    private String priorite;
    private LocalDateTime dateCreation;

    // Sous-objets allégés, dans une forme compatible avec ce que le widget
    // ReclamationCard (mobile) sait déjà lire pour "Mes réclamations".
    private CategorieLegere categorie;
    private LocalisationLegere localisation;

    private String citoyenPrenom; // seule info sur l'auteur, jamais son email/téléphone

    private long nombreVotes;
    private boolean aVote; // le citoyen connecté a-t-il déjà voté pour celle-ci ?

    @Getter
    @Setter
    @AllArgsConstructor
    public static class CategorieLegere {
        private String nom;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class LocalisationLegere {
        private Double latitude;
        private Double longitude;
        private String ville;
    }
}