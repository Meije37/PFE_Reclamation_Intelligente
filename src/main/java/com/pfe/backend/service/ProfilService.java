package com.pfe.backend.service;

import com.pfe.backend.dto.ChangerMotDePasseDTO;
import com.pfe.backend.dto.ProfilUpdateDTO;
import com.pfe.backend.dto.UtilisateurResponseDTO;
import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.exception.AccesRefuseException;
import com.pfe.backend.exception.ResourceNotFoundException;
import com.pfe.backend.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion du profil de l'utilisateur CONNECTÉ, par opposition à
 * UtilisateurServiceImpl qui gère n'importe quel utilisateur par ID pour
 * l'admin. Volontairement séparé : les règles ne sont pas les mêmes
 * (vérification de l'ancien mot de passe ici, jamais côté admin ; pas de
 * changement de rôle/email ici, possible côté admin).
 */
@Service
@RequiredArgsConstructor
public class ProfilService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UtilisateurResponseDTO monProfil(String email) {
        return versDTO(trouver(email));
    }

    @Transactional
    public UtilisateurResponseDTO modifierProfil(String email, ProfilUpdateDTO dto) {
        Utilisateur utilisateur = trouver(email);

        if (utilisateurRepository.existsByTelephoneAndIdNot(dto.getTelephone(), utilisateur.getId())) {
            throw new IllegalArgumentException("Ce numéro de téléphone est déjà utilisé par un autre compte.");
        }

        utilisateur.setNom(dto.getNom());
        utilisateur.setPrenom(dto.getPrenom());
        utilisateur.setTelephone(dto.getTelephone());

        return versDTO(utilisateurRepository.save(utilisateur));
    }

    @Transactional
    public void changerMotDePasse(String email, ChangerMotDePasseDTO dto) {
        Utilisateur utilisateur = trouver(email);

        if (!passwordEncoder.matches(dto.getAncienMotDePasse(), utilisateur.getMotDePasse())) {
            throw new AccesRefuseException("L'ancien mot de passe est incorrect.");
        }

        utilisateur.setMotDePasse(passwordEncoder.encode(dto.getNouveauMotDePasse()));
        utilisateurRepository.save(utilisateur);
    }

    private Utilisateur trouver(String email) {
        return utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + email));
    }

    private UtilisateurResponseDTO versDTO(Utilisateur u) {
        return UtilisateurResponseDTO.builder()
                .id(u.getId())
                .nom(u.getNom())
                .prenom(u.getPrenom())
                .email(u.getEmail())
                .telephone(u.getTelephone())
                .actif(u.getActif())
                .role(u.getRole())
                .serviceId(u.getService() != null ? u.getService().getId() : null)
                .serviceNom(u.getService() != null ? u.getService().getNom() : null)
                .build();
    }
}