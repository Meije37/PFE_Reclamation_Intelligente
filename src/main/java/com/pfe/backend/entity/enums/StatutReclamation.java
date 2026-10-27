package com.pfe.backend.entity.enums;

public enum StatutReclamation {
    OUVERTE("ouverte"),
    EN_COURS("en cours de traitement"),
    RESOLUE("résolue"),
    REJETEE("rejetée"),
    FERMEE("fermée"),
    ANNULEE("annulée");

    private final String libelle;

    StatutReclamation(String libelle) {
        this.libelle = libelle;
    }

    /** Texte lisible pour l'utilisateur (au lieu du code technique, ex. REJETEE). */
    public String getLibelle() {
        return libelle;
    }

    /** Un dossier est clos quand il est résolu, rejeté, fermé ou annulé. */
    public boolean estClos() {
        return this == RESOLUE || this == REJETEE || this == FERMEE || this == ANNULEE;
    }
}