package com.pfe.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ZoneRequestDTO {

    @NotBlank(message = "Le nom de la zone est obligatoire")
    private String nom;

    private String description;
}