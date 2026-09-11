package com.pfe.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
public class DashboardChartsDTO {

    // Nombre de réclamations par statut (OUVERTE, EN_COURS, RESOLUE, REJETEE, ANNULEE)
    private Map<String, Long> parStatut;

    // Nombre de réclamations par priorité (CRITIQUE, HAUTE, MOYENNE, BASSE)
    private Map<String, Long> parPriorite;

    // Nombre de réclamations par catégorie (nom de la catégorie -> nombre)
    private Map<String, Long> parCategorie;

    // Évolution du nombre de réclamations créées par jour sur les 14 derniers jours
    private List<PointEvolution> evolution14Jours;

    @Getter
    @Setter
    @AllArgsConstructor
    public static class PointEvolution {
        private String date;   // format "dd/MM"
        private long total;
    }
}