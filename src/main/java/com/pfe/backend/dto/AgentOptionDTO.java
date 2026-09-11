package com.pfe.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Représentation minimale d'un agent, pour peupler un menu déroulant. */
@Getter
@AllArgsConstructor
public class AgentOptionDTO {
    private Long id;
    private String nom;
    private String prenom;
}