package com.pfe.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** Mise à jour de son propre profil : pas d'email ni de rôle ici (volontairement
 *  absents — changer son propre email/rôle est une action sensible qui, si
 *  besoin un jour, mérite son propre flux avec vérification, pas un simple champ). */
@Getter
@Setter
public class ProfilUpdateDTO {
    @NotBlank(message = "Le nom est requis.")
    private String nom;

    @NotBlank(message = "Le prénom est requis.")
    private String prenom;

    @NotBlank(message = "Le téléphone est requis.")
    @Pattern(regexp = "^[234]\\d{7}$", message = "Format de téléphone mauritanien invalide")
    private String telephone;
}