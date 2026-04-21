package com.pfe.backend.service.impl;

import com.pfe.backend.dto.ServiceRequestDTO;
import com.pfe.backend.dto.ServiceResponseDTO;
import com.pfe.backend.entity.Zone;
import com.pfe.backend.repository.ServiceRepository;
import com.pfe.backend.repository.ZoneRepository;
import com.pfe.backend.service.ServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceServiceImpl implements ServiceService {

    private final ServiceRepository serviceRepository;
    private final ZoneRepository zoneRepository;

    @Override
    public ServiceResponseDTO createService(ServiceRequestDTO request) {
        if (serviceRepository.existsByNom(request.getNom())) {
            throw new RuntimeException("Un service avec ce nom existe déjà");
        }

        if (serviceRepository.existsByEmailService(request.getEmailService())) {
            throw new RuntimeException("Un service avec cet email existe déjà");
        }

        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new RuntimeException("Zone introuvable avec l'id : " + request.getZoneId()));

        com.pfe.backend.entity.Service service = new com.pfe.backend.entity.Service();
        service.setNom(request.getNom());
        service.setDescription(request.getDescription());
        service.setEmailService(request.getEmailService());
        service.setActif(request.getActif() != null ? request.getActif() : true);
        service.setZone(zone);

        com.pfe.backend.entity.Service savedService = serviceRepository.save(service);

        return mapToResponse(savedService);
    }

    @Override
    public ServiceResponseDTO updateService(Long id, ServiceRequestDTO request) {
        com.pfe.backend.entity.Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service introuvable avec l'id : " + id));

        if (!service.getNom().equalsIgnoreCase(request.getNom())
                && serviceRepository.existsByNom(request.getNom())) {
            throw new RuntimeException("Un service avec ce nom existe déjà");
        }

        if (!service.getEmailService().equalsIgnoreCase(request.getEmailService())
                && serviceRepository.existsByEmailService(request.getEmailService())) {
            throw new RuntimeException("Un service avec cet email existe déjà");
        }

        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new RuntimeException("Zone introuvable avec l'id : " + request.getZoneId()));

        service.setNom(request.getNom());
        service.setDescription(request.getDescription());
        service.setEmailService(request.getEmailService());
        service.setActif(request.getActif() != null ? request.getActif() : service.getActif());
        service.setZone(zone);

        com.pfe.backend.entity.Service updatedService = serviceRepository.save(service);

        return mapToResponse(updatedService);
    }

    @Override
    public List<ServiceResponseDTO> getAllServices() {
        return serviceRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public ServiceResponseDTO getServiceById(Long id) {
        com.pfe.backend.entity.Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service introuvable avec l'id : " + id));

        return mapToResponse(service);
    }

    @Override
    public void deleteService(Long id) {
        com.pfe.backend.entity.Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service introuvable avec l'id : " + id));

        serviceRepository.delete(service);
    }

    private ServiceResponseDTO mapToResponse(com.pfe.backend.entity.Service service) {
        return new ServiceResponseDTO(
                service.getId(),
                service.getNom(),
                service.getDescription(),
                service.getEmailService(),
                service.getActif(),
                service.getZone().getId(),
                service.getZone().getNom()
        );
    }
}