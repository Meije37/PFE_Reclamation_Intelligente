package com.pfe.backend.controller;

import com.pfe.backend.dto.DashboardChartsDTO;
import com.pfe.backend.dto.DashboardStatsDTO;
import com.pfe.backend.service.DashboardService;
import com.pfe.backend.service.EvaluationService;
import com.pfe.backend.service.TableauDeBordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;
    private final EvaluationService evaluationService;
    private final TableauDeBordService tableauDeBordService;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDTO> getStats() {
        return ResponseEntity.ok(dashboardService.getDashboardStats());
    }

    @GetMapping("/charts")
    public ResponseEntity<DashboardChartsDTO> getCharts() {
        return ResponseEntity.ok(dashboardService.getDashboardCharts());
    }

    /** GET /api/admin/dashboard/apercu — chiffres clés et bloc « À surveiller » (dont les cas signalés par l'IA). */
    @GetMapping("/apercu")
    public ResponseEntity<Map<String, Object>> getApercu() {
        return ResponseEntity.ok(tableauDeBordService.apercuAdmin());
    }

    /** GET /api/admin/dashboard/satisfaction — étape 8 : avis des citoyens (moyenne, répartition, par agent). */
    @GetMapping("/satisfaction")
    public ResponseEntity<Map<String, Object>> getSatisfaction() {
        return ResponseEntity.ok(evaluationService.statistiques());
    }
}
