package com.pfe.backend.service;

import com.pfe.backend.entity.OTPVerification;
import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.entity.enums.CanalOTP;
import com.pfe.backend.repository.OTPVerificationRepository;
import com.pfe.backend.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UtilisateurRepository utilisateurRepository;
    private final OTPVerificationRepository otpRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    private static final int DUREE_VALIDITE_MINUTES = 10;
    private final SecureRandom random = new SecureRandom();

    /**
     * Génère un code OTP à 6 chiffres et l'envoie par email.
     * Ne révèle jamais si l'email existe ou non en base (sécurité) :
     * la réponse est identique dans les deux cas côté contrôleur.
     */
    @Transactional
    public void demanderReinitialisation(String email) {
        Utilisateur utilisateur = utilisateurRepository.findByEmail(email).orElse(null);

        // Email inconnu : on ne fait rien, mais on ne le dit pas à l'appelant
        if (utilisateur == null) return;

        // Un seul code actif à la fois par email
        otpRepository.deleteByCible(email);

        String code = genererCode6Chiffres();

        OTPVerification otp = new OTPVerification();
        otp.setCible(email);
        otp.setCode(code);
        otp.setCanal(CanalOTP.EMAIL);
        otp.setDateCreation(LocalDateTime.now());
        otp.setDateExpiration(LocalDateTime.now().plusMinutes(DUREE_VALIDITE_MINUTES));
        otpRepository.save(otp);

        emailService.envoyerCodeOtp(email, code);
    }

    /**
     * Vérifie le code OTP et, s'il est valide et non expiré,
     * met à jour le mot de passe de l'utilisateur.
     */
    @Transactional
    public void reinitialiserMotDePasse(String email, String code, String nouveauMotDePasse) {
        OTPVerification otp = otpRepository.findByCibleAndCode(email, code)
                .orElseThrow(() -> new RuntimeException("Code invalide."));

        if (otp.getDateExpiration().isBefore(LocalDateTime.now())) {
            otpRepository.delete(otp);
            throw new RuntimeException("Ce code a expiré. Veuillez en demander un nouveau.");
        }

        Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable."));

        utilisateur.setMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
        utilisateurRepository.save(utilisateur);

        // Le code ne doit plus jamais être réutilisable
        otpRepository.delete(otp);
    }

    private String genererCode6Chiffres() {
        int n = 100000 + random.nextInt(900000); // toujours 6 chiffres (100000-999999)
        return String.valueOf(n);
    }
}