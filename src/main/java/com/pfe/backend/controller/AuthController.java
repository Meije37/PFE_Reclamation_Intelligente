package com.pfe.backend.controller;

import com.pfe.backend.dto.LoginRequestDTO;
import com.pfe.backend.dto.LoginResponseDTO;
import com.pfe.backend.dto.RefreshTokenRequestDTO;
import com.pfe.backend.dto.RegisterRequestDTO;
import com.pfe.backend.dto.RegisterResponseDTO;
import com.pfe.backend.dto.ForgotPasswordRequestDTO;
import com.pfe.backend.dto.ResetPasswordRequestDTO;
import com.pfe.backend.entity.RefreshToken;
import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.repository.UtilisateurRepository;
import com.pfe.backend.service.JwtService;
import com.pfe.backend.service.RefreshTokenService;
import com.pfe.backend.service.PasswordResetService;
import com.pfe.backend.service.UtilisateurService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UtilisateurService utilisateurService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UtilisateurRepository utilisateurRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;


    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> registerCitizen(
            @Valid @RequestBody RegisterRequestDTO request) {

        RegisterResponseDTO response = utilisateurService.registerCitizen(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getMotDePasse()
                )
        );

        Utilisateur utilisateur = utilisateurRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        String token = jwtService.generateToken(utilisateur.getEmail(), utilisateur.getRole().name());
        RefreshToken refreshToken = refreshTokenService.creer(utilisateur);

        LoginResponseDTO response = new LoginResponseDTO(
                token,
                refreshToken.getToken(),
                utilisateur.getEmail(),
                utilisateur.getRole().name()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Renouvelle un token d'accès à partir d'un refresh token encore valide.
     * Rotation du refresh token à chaque appel (un nouveau est généré,
     * l'ancien devient inutilisable) pour limiter les risques en cas de vol.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequestDTO request) {
        try {
            RefreshToken existant = refreshTokenService.trouverParToken(request.getRefreshToken())
                    .orElseThrow(() -> new RuntimeException("Refresh token invalide."));

            RefreshToken valide = refreshTokenService.verifierValidite(existant);
            Utilisateur utilisateur = valide.getUtilisateur();

            String nouveauToken = jwtService.generateToken(utilisateur.getEmail(), utilisateur.getRole().name());
            RefreshToken nouveauRefreshToken = refreshTokenService.creer(utilisateur);

            LoginResponseDTO response = new LoginResponseDTO(
                    nouveauToken,
                    nouveauRefreshToken.getToken(),
                    utilisateur.getEmail(),
                    utilisateur.getRole().name()
            );

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Déconnexion explicite : révoque le refresh token côté serveur pour
     * qu'il ne puisse plus être réutilisé, même si le client le conserve.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequestDTO request) {
        refreshTokenService.trouverParToken(request.getRefreshToken())
                .ifPresent(rt -> refreshTokenService.revoquerPourUtilisateur(rt.getUtilisateur()));
        return ResponseEntity.noContent().build();
    }

    /**
     * Demande de réinitialisation : envoie un code OTP à 6 chiffres par
     * email. Répond toujours de la même façon, que l'email existe ou non
     * en base, pour ne pas révéler quels emails sont enregistrés.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody ForgotPasswordRequestDTO request) {
        passwordResetService.demanderReinitialisation(request.getEmail());
        return ResponseEntity.ok(Map.of(
                "message", "Si cet email est associé à un compte, un code de vérification a été envoyé."
        ));
    }

    /**
     * Vérifie le code OTP et met à jour le mot de passe si tout est valide.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequestDTO request) {
        try {
            passwordResetService.reinitialiserMotDePasse(
                    request.getEmail(),
                    request.getCode(),
                    request.getNouveauMotDePasse()
            );
            return ResponseEntity.ok(Map.of("message", "Mot de passe réinitialisé avec succès."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}