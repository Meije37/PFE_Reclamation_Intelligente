package com.pfe.backend.repository;

import com.pfe.backend.entity.Affectation;
import com.pfe.backend.entity.enums.StatutReclamation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AffectationRepository extends JpaRepository<Affectation, Long> {

    // Toutes les affectations d'un agent
    List<Affectation> findByAgentId(Long agentId);

    // Affectations d'un agent pour une réclamation précise
    List<Affectation> findByAgentIdAndReclamationId(Long agentId, Long reclamationId);

    // Toutes les affectations d'une réclamation (généralement une seule active,
    // mais on garde l'historique) — utile pour retrouver l'agent assigné
    List<Affectation> findByReclamationIdOrderByDateAffectationDesc(Long reclamationId);

    /**
     * Charge actuelle d'un agent : réclamations NON closes dont il est le responsable actuel
     * (sa propre affectation est la plus récente du dossier). Même calcul que l'écran d'affectation.
     */
    @Query("""
        SELECT COUNT(a) FROM Affectation a
        WHERE a.agent.id = :agentId
          AND a.reclamation.statut NOT IN :statutsClos
          AND a.dateAffectation = (SELECT MAX(a2.dateAffectation) FROM Affectation a2
                                   WHERE a2.reclamation = a.reclamation)
    """)
    long countDossiersActifs(@Param("agentId") Long agentId,
                             @Param("statutsClos") Collection<StatutReclamation> statutsClos);

    long countByAgentId(Long agentId);
}
