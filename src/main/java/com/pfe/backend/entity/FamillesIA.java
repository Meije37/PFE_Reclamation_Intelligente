package com.pfe.backend.entity;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Familles d'incidents connues du service IA (voir FAMILLE_INCIDENT dans pfe-ai-service/app/moteur.py),
 * et déduction de la famille d'un SERVICE municipal d'après son nom.
 *
 * Exemple : le service « Voirie et Infrastructures » → VOIRIE_INFRASTRUCTURES. Ainsi, une catégorie
 * liée à ce service est rattachée automatiquement à cette famille, sans configuration.
 */
public final class FamillesIA {

    private FamillesIA() { }

    public static final String EAU = "EAU_ASSAINISSEMENT";
    public static final String ELECTRICITE = "ELECTRICITE_ECLAIRAGE";
    public static final String VOIRIE = "VOIRIE_INFRASTRUCTURES";
    public static final String PROPRETE = "PROPRETE_DECHETS";
    public static final String ESPACES = "ESPACES_PUBLICS";

    public static final List<String> TOUTES = List.of(EAU, ELECTRICITE, VOIRIE, PROPRETE, ESPACES);

    /** Mots-clés (sans accents, en minuscules) cherchés dans le nom du service. */
    private static final Map<String, List<String>> MOTS_CLES = new LinkedHashMap<>();
    static {
        MOTS_CLES.put(EAU, List.of("eau", "assainissement", "hydraul", "egout"));
        MOTS_CLES.put(ELECTRICITE, List.of("electric", "eclairage", "energie", "lumiere"));
        MOTS_CLES.put(VOIRIE, List.of("voirie", "route", "infrastructure", "chaussee", "travaux"));
        MOTS_CLES.put(PROPRETE, List.of("proprete", "dechet", "ordure", "hygiene", "salubrite", "nettoiement"));
        MOTS_CLES.put(ESPACES, List.of("espace", "jardin", "parc", "vert", "marche"));
    }

    /** Familles reconnues dans un nom de service (souvent une seule ; vide si rien ne correspond). */
    public static List<String> depuisNomService(String nom) {
        if (nom == null || nom.isBlank()) return List.of();
        String n = Normalizer.normalize(nom, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        // « eau » doit être un mot entier (sinon « réseau », « bureau »… seraient reconnus).
        String mots = " " + n.replaceAll("[^a-z]+", " ") + " ";
        return MOTS_CLES.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(k ->
                        k.equals("eau") ? mots.contains(" eau ") : n.contains(k)))
                .map(Map.Entry::getKey)
                .toList();
    }
}
