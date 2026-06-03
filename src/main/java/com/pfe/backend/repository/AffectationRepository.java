package com.pfe.backend.repository;

import com.pfe.backend.entity.Affectation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AffectationRepository extends JpaRepository<Affectation, Long> {

    // Toutes les affectations d'un agent
    List<Affectation> findByAgentId(Long agentId);

    // Affectations d'un agent pour une réclamation précise
    List<Affectation> findByAgentIdAndReclamationId(Long agentId, Long reclamationId);
}