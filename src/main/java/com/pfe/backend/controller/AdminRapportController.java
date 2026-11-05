package com.pfe.backend.controller;

import com.pfe.backend.service.RapportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

/**
 * Rapports d'activité pour l'administration (/api/admin/** : rôle ADMIN).
 *
 *  GET /api/admin/rapports/synthese?du=2026-10-01&au=2026-10-31 → chiffres du rapport (JSON)
 *  GET /api/admin/rapports/excel?du=…&au=…                      → classeur .xlsx
 *
 * Sans dates : les 30 derniers jours.
 */
@RestController
@RequestMapping("/api/admin/rapports")
@RequiredArgsConstructor
public class AdminRapportController {

    private final RapportService rapportService;

    @GetMapping("/synthese")
    public ResponseEntity<?> synthese(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        LocalDate[] p = periode(du, au);
        if (p == null) return ResponseEntity.badRequest().body(Map.of("message", "La date de début doit précéder la date de fin."));
        return ResponseEntity.ok(rapportService.synthese(p[0], p[1]));
    }

    @GetMapping("/excel")
    public ResponseEntity<?> excel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) throws IOException {
        LocalDate[] p = periode(du, au);
        if (p == null) return ResponseEntity.badRequest().body(Map.of("message", "La date de début doit précéder la date de fin."));
        byte[] fichier = rapportService.excel(p[0], p[1]);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"rapport_chikayti_" + p[0] + "_" + p[1] + ".xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(fichier);
    }

    /** Période par défaut : 30 derniers jours ; null si début > fin. */
    private static LocalDate[] periode(LocalDate du, LocalDate au) {
        LocalDate fin = au != null ? au : LocalDate.now();
        LocalDate debut = du != null ? du : fin.minusDays(29);
        return debut.isAfter(fin) ? null : new LocalDate[]{debut, fin};
    }
}
