package com.pfe.backend.repository;

import com.pfe.backend.entity.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    Optional<Evaluation> findByReclamation_Id(Long reclamationId);

    boolean existsByReclamation_Id(Long reclamationId);

    /** Avis d'un agent (pour sa note moyenne sur son tableau de bord). */
    List<Evaluation> findByAgent_Id(Long agentId);

    List<Evaluation> findAllByOrderByDateEvaluationDesc();

    /** Notes d'une liste de réclamations (une seule requête pour la liste de l'agent). */
    List<Evaluation> findByReclamation_IdIn(java.util.Collection<Long> reclamationIds);
}
