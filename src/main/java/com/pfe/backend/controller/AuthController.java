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
import com.pfe.backend.service.ProtectionConnexionService;
import com.pfe.backend.service.UtilisateurService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

// CORS : géré une seule fois, globalement, dans SecurityConfig (pas d'@CrossOrigin par contrôleur).
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
    private final ProtectionConnexionService protection;


    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> registerCitizen(
            @Valid @RequestBody RegisterRequestDTO request) {

        RegisterResponseDTO response = utilisateurService.registerCitizen(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Connexion. Protégée contre les essais en série : après 5 mots de passe faux pour un même
     * e-mail (ou 20 depuis une même adresse IP) en 15 minutes, la connexion est refusée pendant
     * 15 minutes avec le code HTTP 429 « Too Many Requests ».
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO request, HttpServletRequest http) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim();
        String ip = http.getRemoteAddr();

        long attente = protection.attenteConnexion(email, ip);
        if (attente > 0) return tropDeTentatives(attente);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getMotDePasse()));
        } catch (DisabledException | LockedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "code", "COMPTE_DESACTIVE",
                    "message", "Ce compte est désactivé. Contactez l'administration de la commune."));
        } catch (AuthenticationException e) {
            // Même réponse que l'e-mail existe ou non : on ne révèle pas quels comptes existent.
            int restants = protection.echecConnexion(email, ip);
            if (restants == 0) return tropDeTentatives(protection.attenteConnexion(email, ip));
            Map<String, Object> corps = new LinkedHashMap<>();
            corps.put("code", "IDENTIFIANTS_INVALIDES");
            corps.put("message", restants <= 2
                    ? "Email ou mot de passe incorrect. Encore " + restants + " essai" + (restants > 1 ? "s" : "")
                      + " avant un blocage temporaire."
                    : "Email ou mot de passe incorrect.");
            corps.put("tentativesRestantes", restants);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(corps);
        }

        protection.succesConnexion(email);
        Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
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

    /** Réponse 429 : trop de tentatives, avec le délai d'attente (en-tête Retry-After + JSON). */
    private static ResponseEntity<Map<String, Object>> tropDeTentatives(long secondes) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("code", "TROP_DE_TENTATIVES");
        corps.put("message", "Trop de tentatives. Pour votre sécurité, réessayez dans "
                + ProtectionConnexionService.duree(secondes) + ".");
        corps.put("reessayerDansSecondes", secondes);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(secondes))
                .body(corps);
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
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequestDTO request, HttpServletRequest http) {
        // Limite : 3 demandes par e-mail et 10 par adresse IP en 15 minutes (pas d'inondation de mails).
        long attente = protection.demandeCode(request.getEmail(), http.getRemoteAddr());
        if (attente > 0) return tropDeTentatives(attente);
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
        // Code à 6 chiffres = 1 million de possibilités : 5 codes faux → 15 minutes de blocage.
        long attente = protection.attenteCode(request.getEmail());
        if (attente > 0) return tropDeTentatives(attente);
        try {
            passwordResetService.reinitialiserMotDePasse(
                    request.getEmail(),
                    request.getCode(),
                    request.getNouveauMotDePasse()
            );
            protection.succesCode(request.getEmail());
            return ResponseEntity.ok(Map.of("message", "Mot de passe réinitialisé avec succès."));
        } catch (RuntimeException e) {
            if ("Code invalide.".equals(e.getMessage()) && protection.echecCode(request.getEmail()) == 0) {
                return tropDeTentatives(protection.attenteCode(request.getEmail()));
            }
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}