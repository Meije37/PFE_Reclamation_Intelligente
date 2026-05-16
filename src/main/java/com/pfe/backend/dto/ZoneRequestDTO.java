package com.pfe.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ZoneRequestDTO {

    @NotBlank(message = "Le nom de la zone est obligatoire")
    @Size(min = 3, max = 50, message = "Le nom doit contenir entre 3 et 50 caractères")
    private String nom;

    @NotBlank(message = "Le type de zone est obligatoire")
    private String typeZone;

    @Size(max = 255, message = "La description ne doit pas dépasser 255 caractères")
    private String description;
}