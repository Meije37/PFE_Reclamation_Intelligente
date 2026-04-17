package com.pfe.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceRequestDTO {

    @NotBlank(message = "Le nom du service est obligatoire")
    private String nom;

    private String description;

    @Email(message = "Email invalide")
    @NotBlank(message = "L'email du service est obligatoire")
    private String emailService;

    @NotNull(message = "L'id de la zone est obligatoire")
    private Long zoneId;

    private Boolean actif;
}