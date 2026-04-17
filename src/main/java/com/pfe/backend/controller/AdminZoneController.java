package com.pfe.backend.controller;

import com.pfe.backend.dto.ZoneRequestDTO;
import com.pfe.backend.dto.ZoneResponseDTO;
import com.pfe.backend.service.ZoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/zones")
@RequiredArgsConstructor
public class AdminZoneController {

    private final ZoneService zoneService;

    @PostMapping
    public ResponseEntity<ZoneResponseDTO> createZone(@Valid @RequestBody ZoneRequestDTO request) {
        ZoneResponseDTO response = zoneService.createZone(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ZoneResponseDTO> updateZone(@PathVariable Long id,
                                                      @Valid @RequestBody ZoneRequestDTO request) {
        ZoneResponseDTO response = zoneService.updateZone(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ZoneResponseDTO>> getAllZones() {
        return ResponseEntity.ok(zoneService.getAllZones());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ZoneResponseDTO> getZoneById(@PathVariable Long id) {
        return ResponseEntity.ok(zoneService.getZoneById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteZone(@PathVariable Long id) {
        zoneService.deleteZone(id);
        return ResponseEntity.ok("Zone supprimée avec succès");
    }
}