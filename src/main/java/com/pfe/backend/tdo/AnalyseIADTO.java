package com.pfe.backend.tdo;

import lombok.Data;

@Data
public class AnalyseIADTO {
    private Long reclamationId;
    private String typeAnalyse;
    private Double scoreConfiance;
    private String categoriePredite;
    private Boolean estDoublon;
}
