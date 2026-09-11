package com.pfe.backend.repository;

import com.pfe.backend.entity.RefreshToken;
import com.pfe.backend.entity.Utilisateur;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);
    @Transactional
    void deleteByUtilisateur(Utilisateur utilisateur);
}