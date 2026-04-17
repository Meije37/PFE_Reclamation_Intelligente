package com.pfe.backend.service.impl;

import com.pfe.backend.dto.*;
import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.entity.enums.Role;
import com.pfe.backend.repository.UtilisateurRepository;
import com.pfe.backend.service.UtilisateurService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UtilisateurServiceImpl implements UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public RegisterResponseDTO registerCitizen(RegisterRequestDTO request) {

        if (utilisateurRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Cet email est déjà utilisé");
        }

        if (utilisateurRepository.existsByTelephone(request.getTelephone())) {
            throw new RuntimeException("Ce téléphone est déjà utilisé");
        }

        Utilisateur utilisateur = Utilisateur.builder()
                .nom(request.getNom())
                .prenom(request.getPrenom())
                .email(request.getEmail())
                .telephone(request.getTelephone())
                .motDePasse(passwordEncoder.encode(request.getMotDePasse()))
                .role(Role.CITOYEN)
                .actif(true)
                .build();

        Utilisateur savedUser = utilisateurRepository.save(utilisateur);

        return new RegisterResponseDTO(
                "Inscription réussie",
                savedUser.getId(),
                savedUser.getNom(),
                savedUser.getPrenom(),
                savedUser.getEmail(),
                savedUser.getTelephone()
        );
    }

    @Override
    public UtilisateurResponseDTO createUtilisateur(UtilisateurCreateDTO dto) {

        if (utilisateurRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Cet email est déjà utilisé");
        }

        if (utilisateurRepository.existsByTelephone(dto.getTelephone())) {
            throw new RuntimeException("Ce téléphone est déjà utilisé");
        }

        Utilisateur utilisateur = Utilisateur.builder()
                .nom(dto.getNom())
                .prenom(dto.getPrenom())
                .email(dto.getEmail())
                .telephone(dto.getTelephone())
                .motDePasse(passwordEncoder.encode(dto.getMotDePasse()))
                .role(dto.getRole())
                .actif(true)
                .build();

        return mapToResponse(utilisateurRepository.save(utilisateur));
    }

    @Override
    public List<UtilisateurResponseDTO> getAllUtilisateurs() {
        return utilisateurRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public UtilisateurResponseDTO getUtilisateurById(Long id) {
        Utilisateur utilisateur = utilisateurRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        return mapToResponse(utilisateur);
    }

    @Override
    public UtilisateurResponseDTO updateUtilisateur(Long id, UtilisateurUpdateDTO dto) {

        Utilisateur utilisateur = utilisateurRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        if (dto.getEmail() != null && !dto.getEmail().equals(utilisateur.getEmail())) {
            if (utilisateurRepository.existsByEmailAndIdNot(dto.getEmail(), id)) {
                throw new RuntimeException("Cet email est déjà utilisé");
            }
            utilisateur.setEmail(dto.getEmail());
        }

        if (dto.getTelephone() != null && !dto.getTelephone().equals(utilisateur.getTelephone())) {
            if (utilisateurRepository.existsByTelephoneAndIdNot(dto.getTelephone(), id)) {
                throw new RuntimeException("Ce téléphone est déjà utilisé");
            }
            utilisateur.setTelephone(dto.getTelephone());
        }

        if (dto.getNom() != null) {
            utilisateur.setNom(dto.getNom());
        }

        if (dto.getPrenom() != null) {
            utilisateur.setPrenom(dto.getPrenom());
        }

        if (dto.getRole() != null) {
            utilisateur.setRole(dto.getRole());
        }

        if (dto.getActif() != null) {
            utilisateur.setActif(dto.getActif());
        }

        return mapToResponse(utilisateurRepository.save(utilisateur));
    }

    @Override
    public UtilisateurResponseDTO desactiverUtilisateur(Long id) {
        Utilisateur utilisateur = utilisateurRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        utilisateur.setActif(false);
        return mapToResponse(utilisateurRepository.save(utilisateur));
    }

    @Override
    public UtilisateurResponseDTO activerUtilisateur(Long id) {
        Utilisateur utilisateur = utilisateurRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        utilisateur.setActif(true);
        return mapToResponse(utilisateurRepository.save(utilisateur));
    }

    @Override
    public void deleteUtilisateur(Long id) {
        Utilisateur utilisateur = utilisateurRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        utilisateurRepository.delete(utilisateur);
    }

    private UtilisateurResponseDTO mapToResponse(Utilisateur utilisateur) {
        return UtilisateurResponseDTO.builder()
                .id(utilisateur.getId())
                .nom(utilisateur.getNom())
                .prenom(utilisateur.getPrenom())
                .email(utilisateur.getEmail())
                .telephone(utilisateur.getTelephone())
                .actif(utilisateur.getActif())
                .role(utilisateur.getRole())
                .build();
    }
}