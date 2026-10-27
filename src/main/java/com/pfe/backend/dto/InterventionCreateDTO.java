package com.pfe.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Planification d'une intervention terrain : agent choisi, ou attribution automatique. */
@Data
public class InterventionCreateDTO {
    @NotNull(message = "Le service responsable de l'intervention est requis.")
    private Long serviceId;

    /** Optionnel : si absent, le système choisit l'agent actif le moins chargé du service. */
    private Long agentId;

    /** Optionnel : si absent, l'intervention démarre "maintenant". */
    private String dateDebut; // ISO-8601, ex: 2026-08-15T09:00:00
}