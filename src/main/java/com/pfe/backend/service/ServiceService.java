package com.pfe.backend.service;

import com.pfe.backend.dto.ServiceRequestDTO;
import com.pfe.backend.dto.ServiceResponseDTO;

import java.util.List;

public interface ServiceService {

    ServiceResponseDTO createService(ServiceRequestDTO request);

    ServiceResponseDTO updateService(Long id, ServiceRequestDTO request);

    List<ServiceResponseDTO> getAllServices();

    ServiceResponseDTO getServiceById(Long id);

    void deleteService(Long id);
}