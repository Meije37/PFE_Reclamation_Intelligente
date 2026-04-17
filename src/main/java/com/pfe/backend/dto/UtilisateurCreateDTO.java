package com.pfe.backend.dto;

import com.pfe.backend.entity.enums.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UtilisateurCreateDTO {
    private String nom;
    private String prenom;
    private String email;
    private String motDePasse;
    private String telephone;
    private Role role;
}