package com.pfe.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Planification d'une intervention terrain, confiée à un agent précis. */
@Data
public class InterventionCreateDTO {
    @NotNull(message = "Le service responsable de l'intervention est requis.")
    private Long serviceId;

    @NotNull(message = "L'agent responsable de l'intervention est requis.")
    private Long agentId;

    /** Optionnel : si absent, l'intervention démarre "maintenant". */
    private String dateDebut; // ISO-8601, ex: 2026-08-15T09:00:00
}