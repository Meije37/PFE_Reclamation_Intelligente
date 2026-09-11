package com.pfe.backend.service;

import com.pfe.backend.dto.DashboardChartsDTO;
import com.pfe.backend.dto.DashboardStatsDTO;

public interface DashboardService {
    DashboardStatsDTO getDashboardStats();
    DashboardChartsDTO getDashboardCharts();
}