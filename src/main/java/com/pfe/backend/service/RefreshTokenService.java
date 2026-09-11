package com.pfe.backend.service;

import com.pfe.backend.entity.RefreshToken;
import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    /**
     * Crée un nouveau refresh token pour l'utilisateur. Un utilisateur ne
     * garde qu'un seul refresh token actif à la fois (les anciens sont
     * supprimés) — évite l'accumulation de tokens morts en base.
     */
    @Transactional
    public RefreshToken creer(Utilisateur utilisateur) {
        refreshTokenRepository.deleteByUtilisateur(utilisateur);

        RefreshToken refreshToken = RefreshToken.builder()
                .utilisateur(utilisateur)
                .token(UUID.randomUUID().toString())
                .dateExpiration(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    public Optional<RefreshToken> trouverParToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    /**
     * Vérifie que le refresh token n'est ni expiré ni révoqué.
     * Le supprime automatiquement s'il est expiré.
     */
    @Transactional
    public RefreshToken verifierValidite(RefreshToken refreshToken) {
        if (refreshToken.isRevoked() || refreshToken.getDateExpiration().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new RuntimeException("Refresh token expiré ou révoqué. Veuillez vous reconnecter.");
        }
        return refreshToken;
    }

    @Transactional
    public void revoquerPourUtilisateur(Utilisateur utilisateur) {
        refreshTokenRepository.deleteByUtilisateur(utilisateur);
    }
}