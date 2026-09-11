package com.pfe.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Mise à jour de statut : EN_COURS -> TERMINEE renseigne resultat/coutEstime. */
@Data
public class InterventionStatutUpdateDTO {
    @NotBlank
    private String statut; // PLANIFIEE, EN_COURS, TERMINEE, ANNULEE
    private String resultat;
    private Double coutEstime;
}