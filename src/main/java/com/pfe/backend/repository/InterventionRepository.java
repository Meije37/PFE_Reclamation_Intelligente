package com.pfe.backend.repository;

import com.pfe.backend.entity.Intervention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterventionRepository extends JpaRepository<Intervention, Long> {

    List<Intervention> findByReclamationIdOrderByDateDebutDesc(Long reclamationId);

    List<Intervention> findByAgentIdOrderByDateDebutDesc(Long agentId);
}