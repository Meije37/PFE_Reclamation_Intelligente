package com.pfe.backend.service;

import com.pfe.backend.dto.ZoneRequestDTO;
import com.pfe.backend.dto.ZoneResponseDTO;

import java.util.List;

public interface ZoneService {

    ZoneResponseDTO createZone(ZoneRequestDTO request);

    ZoneResponseDTO updateZone(Long id, ZoneRequestDTO request);

    List<ZoneResponseDTO> getAllZones();

    ZoneResponseDTO getZoneById(Long id);

    void deleteZone(Long id);
}