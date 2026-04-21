package com.pfe.backend.service;

import com.pfe.backend.dto.RegisterRequestDTO;
import com.pfe.backend.dto.RegisterResponseDTO;
import com.pfe.backend.dto.UtilisateurCreateDTO;
import com.pfe.backend.dto.UtilisateurResponseDTO;
import com.pfe.backend.dto.UtilisateurUpdateDTO;

import java.util.List;

public interface UtilisateurService {

    RegisterResponseDTO registerCitizen(RegisterRequestDTO request);

    UtilisateurResponseDTO createUtilisateur(UtilisateurCreateDTO dto);

    List<UtilisateurResponseDTO> getAllUtilisateurs();

    UtilisateurResponseDTO getUtilisateurById(Long id);

    UtilisateurResponseDTO updateUtilisateur(Long id, UtilisateurUpdateDTO dto);

    UtilisateurResponseDTO desactiverUtilisateur(Long id);

    UtilisateurResponseDTO activerUtilisateur(Long id);

    void deleteUtilisateur(Long id);
}