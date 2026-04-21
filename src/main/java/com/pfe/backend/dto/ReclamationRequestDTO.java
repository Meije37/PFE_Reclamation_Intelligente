package com.pfe.backend.dto;

import lombok.Data;

@Data
public class ReclamationRequestDTO {
    private String titre;
    private String description;
    private Double latitude;
    private Double longitude;
    private String adresse;
    private String quartier;
    private String ville;
    private Long categorieId;

}
