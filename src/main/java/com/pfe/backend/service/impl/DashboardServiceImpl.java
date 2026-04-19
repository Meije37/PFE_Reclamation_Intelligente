package com.pfe.backend.service.impl;

import com.pfe.backend.dto.DashboardStatsDTO;
import com.pfe.backend.repository.*;
import com.pfe.backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final UtilisateurRepository utilisateurRepository;
    private final ZoneRepository zoneRepository;
    private final ServiceRepository serviceRepository;
    private final ReclamationRepository reclamationRepository;
    private final CategorieReclamationRepository categorieRepository;

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
}