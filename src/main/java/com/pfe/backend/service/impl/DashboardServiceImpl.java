package com.pfe.backend.service.impl;

import com.pfe.backend.dto.DashboardChartsDTO;
import com.pfe.backend.dto.DashboardStatsDTO;
import com.pfe.backend.entity.Reclamation;
import com.pfe.backend.repository.*;
import com.pfe.backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final UtilisateurRepository utilisateurRepository;
    private final ZoneRepository zoneRepository;
    private final ServiceRepository serviceRepository;
    private final ReclamationRepository reclamationRepository;
    private final CategorieReclamationRepository categorieRepository;

    private static final DateTimeFormatter FORMAT_JOUR = DateTimeFormatter.ofPattern("dd/MM");

    @Override
    public DashboardStatsDTO getDashboardStats() {

        return new DashboardStatsDTO(
                utilisateurRepository.count(),
                zoneRepository.count(),
                serviceRepository.count(),
                reclamationRepository.count(),
                categorieRepository.count()
        );
    }

    @Override
    public DashboardChartsDTO getDashboardCharts() {
        List<Reclamation> toutes = reclamationRepository.findAll();

        // ---- Répartition par statut ----
        Map<String, Long> parStatut = toutes.stream()
                .filter(r -> r.getStatut() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getStatut().name(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        // ---- Répartition par priorité ----
        Map<String, Long> parPriorite = toutes.stream()
                .filter(r -> r.getPriorite() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getPriorite().name(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        // ---- Répartition par catégorie ----
        Map<String, Long> parCategorie = toutes.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getCategorie() != null ? r.getCategorie().getNom() : "Non catégorisé",
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        // ---- Évolution sur les 14 derniers jours ----
        List<DashboardChartsDTO.PointEvolution> evolution = new ArrayList<>();
        LocalDate aujourdHui = LocalDate.now();

        for (int i = 13; i >= 0; i--) {
            LocalDate jour = aujourdHui.minusDays(i);

            long total = toutes.stream()
                    .filter(r -> r.getDateCreation() != null
                            && r.getDateCreation().toLocalDate().isEqual(jour))
                    .count();

            evolution.add(new DashboardChartsDTO.PointEvolution(jour.format(FORMAT_JOUR), total));
        }

        return new DashboardChartsDTO(parStatut, parPriorite, parCategorie, evolution);
    }
}