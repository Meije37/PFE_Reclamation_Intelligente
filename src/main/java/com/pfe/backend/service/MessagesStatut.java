package com.pfe.backend.service;

import com.pfe.backend.entity.Reclamation;
import com.pfe.backend.entity.enums.StatutReclamation;

/**
 * Textes des notifications envoyées au citoyen quand le statut de sa réclamation change.
 * Remplace l'ancien message qui affichait le code brut (ex. « est passé à : REJETEE »).
 */
public final class MessagesStatut {

    private MessagesStatut() { }

    public static String titre(StatutReclamation statut) {
        return statut == StatutReclamation.REJETEE
                ? "Votre réclamation a été rejetée"
                : "Mise à jour de votre réclamation";
    }

    /**
     * @param motif texte saisi par l'agent ou l'admin ; n'est montré au citoyen que s'il a été
     *              écrit par une personne (jamais les messages automatiques du système).
     */
    public static String message(Reclamation r, StatutReclamation statut, String motif) {
        StringBuilder sb = new StringBuilder();
        sb.append("Votre réclamation \"").append(r.getTitre())
                .append("\" (réf. ").append(r.getReference()).append(") est maintenant : ")
                .append(statut.getLibelle()).append(".");

        String motifPublic = MotifsHistorique.motifPublic(motif);
        if (statut == StatutReclamation.REJETEE && motifPublic != null) {
            sb.append(" Motif : ").append(motifPublic);
        }
        return sb.toString();
    }
}