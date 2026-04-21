package com.pfe.backend.dto;

import com.pfe.backend.entity.enums.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UtilisateurUpdateDTO {
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private Role role;
    private Boolean actif;
}