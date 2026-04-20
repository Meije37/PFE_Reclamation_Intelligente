package com.pfe.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequestDTO {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    private String prenom;

    @Email(message = "Email invalide")
    @NotBlank(message = "L'email est obligatoire")
    private String email;

    @NotBlank(message = "Le téléphone est obligatoire")
    // Regex pour la Mauritanie : commence par 2, 3 ou 4 et fait exactement 8 chiffres
    @Pattern(
            regexp = "^[234]\\d{7}$",
            message = "Le numéro de téléphone doit comporter 8 chiffres et commencer par 2, 3 ou 4"
    )
    private String telephone;

    @NotBlank(message = "Le mot de passe est obligatoire")
    private String motDePasse;

    // ... tes autres champs (nom, email, telephone, etc.)

    // Ajout pour permettre à l'admin de préciser le rôle
    private String role;
}