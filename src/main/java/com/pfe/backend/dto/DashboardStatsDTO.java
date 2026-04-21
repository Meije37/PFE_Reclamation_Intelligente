package com.pfe.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class DashboardStatsDTO {

    private long nombreUtilisateurs;
    private long nombreZones;
    private long nombreServices;
    private long nombreReclamations;
    private long nombreCategories;
}