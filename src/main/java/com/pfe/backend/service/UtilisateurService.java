package com.pfe.backend.service;

import com.pfe.backend.dto.RegisterRequestDTO;
import com.pfe.backend.dto.RegisterResponseDTO;

public interface UtilisateurService {
    RegisterResponseDTO registerCitizen(RegisterRequestDTO request);
}