package com.pfe.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ServiceResponseDTO {

    private Long id;
    private String nom;
    private String description;
    private String emailService;
    private Boolean actif;
    private Long zoneId;
    private String nomZone;
}