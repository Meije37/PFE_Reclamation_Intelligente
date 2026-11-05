package com.pfe.backend.entity.enums;

/** Décision humaine sur une proposition de l'IA (Human-in-the-Loop). */
public enum TypeFeedbackIA {
    // Doublons (étape 7)
    DOUBLON_CONFIRME,
    DOUBLON_REJETE,
    // Périmètre municipal (modèle is_municipal)
    PERIMETRE_MUNICIPAL_CONFIRME,
    HORS_PERIMETRE_CONFIRME,
    // Priorité (modèle de priorité)
    PRIORITE_CONFIRMEE,
    PRIORITE_CORRIGEE,
    // Catégorie (erreur de catégorie du citoyen, détectée d'après le texte)
    CATEGORIE_CONFIRMEE,
    CATEGORIE_CORRIGEE
}
