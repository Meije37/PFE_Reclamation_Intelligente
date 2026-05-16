package com.pfe.backend.repository;

import com.pfe.backend.entity.Affectation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AffectationRepository extends JpaRepository<Affectation, Long> {
    // Spring va générer la requête SQL automatique : SELECT * FROM affectations WHERE reclamation_id = ? ORDER BY date_affectation DESC
    List<Affectation> findByReclamationIdOrderByDateAffectationDesc(Long reclamationId);
}