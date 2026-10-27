package com.pfe.backend.service;

import java.util.List;
import java.util.Locale;

/**
 * Distingue, dans l'historique des statuts, le MOTIF saisi par une personne
 * (ex : « Hors compétence ») des messages ÉCRITS PAR LE SYSTÈME
 * (ex : « Assignation automatique à l'agent Aziz Mohohamed »).
 *
 * Les seconds nomment un agent et un service : ils servent au personnel interne mais
 * ne doivent jamais être envoyés à un citoyen (l'historique public ne doit pas dire
 * qui a effectué un changement).
 */
public final class MotifsHistorique {

    private MotifsHistorique() { }

    /** Débuts des textes que le serveur génère lui-même (voir ReclamationService et AgentController). */
    private static final List<String> DEBUTS_MESSAGES_SYSTEME = List.of(
            "assignation automatique",
            "affectation manuelle",
            "statut mis à jour automatiquement",
            "mis à jour par l'agent"
    );

    public static boolean estMessageSysteme(String texte) {
        if (texte == null) return false;
        String t = texte.trim().toLowerCase(Locale.ROOT);
        return DEBUTS_MESSAGES_SYSTEME.stream().anyMatch(t::startsWith);
    }

    /** Motif montrable à un citoyen : null s'il est vide ou écrit par le système. */
    public static String motifPublic(String texte) {
        if (texte == null || texte.isBlank() || estMessageSysteme(texte)) return null;
        return texte.trim();
    }
}