package com.pfe.backend.dto;

import lombok.Data;

/** Transfert d'une intervention planifiée : agentId absent = choix automatique dans le même service. */
@Data
public class InterventionTransfertDTO {
    private Long agentId;
}