package com.pfe.backend.service;

import java.util.List;

/**
 * Les 9 moughataas de Nouakchott et le rattachement d'une position GPS à la plus proche.
 * (Même méthode que la carte de l'administration : centres approximatifs, pas les limites officielles.)
 */
public final class Moughataas {

    private Moughataas() { }

    public record Moughataa(String nom, String wilaya, double lat, double lon) { }

    public static final List<Moughataa> TOUTES = List.of(
            new Moughataa("Tevragh Zeina", "Nouakchott-Ouest", 18.1010, -15.9790),
            new Moughataa("Ksar", "Nouakchott-Ouest", 18.1030, -15.9550),
            new Moughataa("Sebkha", "Nouakchott-Ouest", 18.0740, -15.9750),
            new Moughataa("El Mina", "Nouakchott-Sud", 18.0560, -15.9690),
            new Moughataa("Arafat", "Nouakchott-Sud", 18.0580, -15.9380),
            new Moughataa("Riyad", "Nouakchott-Sud", 18.0230, -15.9420),
            new Moughataa("Teyarett", "Nouakchott-Nord", 18.1350, -15.9480),
            new Moughataa("Dar Naim", "Nouakchott-Nord", 18.1170, -15.9200),
            new Moughataa("Toujounine", "Nouakchott-Nord", 18.0880, -15.9100));

    public static final String HORS = "Hors de Nouakchott";
    public static final String INCONNUE = "Position inconnue";

    public static boolean dansNouakchott(Double lat, Double lon) {
        return lat != null && lon != null && lat >= 17.90 && lat <= 18.30 && lon >= -16.10 && lon <= -15.80;
    }

    /** Nom de la moughataa la plus proche, « Hors de Nouakchott » ou « Position inconnue ». */
    public static String de(Double lat, Double lon) {
        if (lat == null || lon == null) return INCONNUE;
        if (!dansNouakchott(lat, lon)) return HORS;
        Moughataa meilleure = TOUTES.get(0);
        double dMin = Double.MAX_VALUE;
        double cos = Math.cos(Math.toRadians(lat));
        for (Moughataa m : TOUTES) {
            double dLat = m.lat() - lat;
            double dLon = (m.lon() - lon) * cos;
            double d = dLat * dLat + dLon * dLon;
            if (d < dMin) { dMin = d; meilleure = m; }
        }
        return meilleure.nom();
    }
}
