package com.pfe.backend.service.impl;

import com.pfe.backend.dto.ZoneRequestDTO;
import com.pfe.backend.dto.ZoneResponseDTO;
import com.pfe.backend.entity.Zone;
import com.pfe.backend.repository.ZoneRepository;
import com.pfe.backend.service.ZoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ZoneServiceImpl implements ZoneService {

    private final ZoneRepository zoneRepository;

    @Override
    public ZoneResponseDTO createZone(ZoneRequestDTO request) {
        if (zoneRepository.existsByNom(request.getNom())) {
            throw new RuntimeException("Une zone avec ce nom existe déjà");
        }

        Zone zone = new Zone();
        zone.setNom(request.getNom());
        zone.setDescription(request.getDescription());

        Zone savedZone = zoneRepository.save(zone);

        return mapToResponse(savedZone);
    }

    @Override
    public ZoneResponseDTO updateZone(Long id, ZoneRequestDTO request) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Zone introuvable avec l'id : " + id));

        if (!zone.getNom().equalsIgnoreCase(request.getNom())
                && zoneRepository.existsByNom(request.getNom())) {
            throw new RuntimeException("Une zone avec ce nom existe déjà");
        }

        zone.setNom(request.getNom());
        zone.setDescription(request.getDescription());

        Zone updatedZone = zoneRepository.save(zone);

        return mapToResponse(updatedZone);
    }

    @Override
    public List<ZoneResponseDTO> getAllZones() {
        return zoneRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public ZoneResponseDTO getZoneById(Long id) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Zone introuvable avec l'id : " + id));

        return mapToResponse(zone);
    }

    @Override
    public void deleteZone(Long id) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Zone introuvable avec l'id : " + id));

        zoneRepository.delete(zone);
    }

    private ZoneResponseDTO mapToResponse(Zone zone) {
        return new ZoneResponseDTO(
                zone.getId(),
                zone.getNom(),
                zone.getDescription()
        );
    }
}