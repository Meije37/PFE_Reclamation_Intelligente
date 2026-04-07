package com.pfe.backend.tdo;

import lombok.Data;

@Data
public class ReclamationDTO {
    private String titre;
    private String description;
    private Double latitude;
    private Double longitude;
    private Long categorieId;

}
