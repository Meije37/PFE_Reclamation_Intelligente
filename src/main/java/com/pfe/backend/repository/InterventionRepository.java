package com.pfe.backend.repository;

import com.pfe.backend.entity.Intervention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterventionRepository extends JpaRepository<Intervention, Long> {

    List<Intervention> findByReclamationIdOrderByDateDebutDesc(Long reclamationId);

    List<Intervention> findByAgentIdOrderByDateDebutDesc(Long agentId);

    /** Vrai si cet agent a au moins une intervention sur cette réclamation. */
    boolean existsByReclamationIdAndAgentId(Long reclamationId, Long agentId);

    /** Charge de travail en cours d'un agent (interventions planifiées ou en cours). */
    long countByAgentIdAndStatutInterventionIn(
            Long agentId, java.util.Collection<com.pfe.backend.entity.enums.StatutIntervention> statuts);

    /** Nombre total d'interventions confiées à un agent (départage deux agents à charge égale). */
    long countByAgentId(Long agentId);

    /**
     * Charge l'intervention en verrouillant sa ligne jusqu'à la fin de la transaction
     * (SELECT ... FOR UPDATE) : deux changements de statut simultanés (ex : l'exécutant et
     * un admin qui cliquent en même temps) sont traités l'un après l'autre, jamais en parallèle.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select i from Intervention i where i.idIntervention = :id")
    java.util.Optional<Intervention> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}