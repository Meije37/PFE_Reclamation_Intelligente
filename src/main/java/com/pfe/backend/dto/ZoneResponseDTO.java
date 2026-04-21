package com.pfe.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ZoneResponseDTO {

    private Long id;
    private String nom;
    private String description;
}