package com.pfe.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Représentation minimale d'un service, pour peupler un menu déroulant
 *  (ex: choix du service responsable d'une intervention). */
@Getter
@AllArgsConstructor
public class ServiceOptionDTO {
    private Long id;
    private String nom;
}