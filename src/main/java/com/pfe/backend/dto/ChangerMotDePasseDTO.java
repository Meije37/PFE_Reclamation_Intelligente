package com.pfe.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangerMotDePasseDTO {
    @NotBlank(message = "L'ancien mot de passe est requis.")
    private String ancienMotDePasse;

    @NotBlank(message = "Le nouveau mot de passe est requis.")
    @Size(min = 6, message = "Le nouveau mot de passe doit contenir au moins 6 caractères.")
    private String nouveauMotDePasse;
}