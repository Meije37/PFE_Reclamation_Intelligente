package com.pfe.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class RegisterResponseDTO {
    private String message;
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
}