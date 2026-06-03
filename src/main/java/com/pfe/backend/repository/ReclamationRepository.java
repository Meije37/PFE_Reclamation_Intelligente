package com.pfe.backend.repository;

import com.pfe.backend.entity.Reclamation;
import com.pfe.backend.entity.enums.Priorite;
import com.pfe.backend.entity.enums.StatutReclamation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReclamationRepository extends JpaRepository<Reclamation, Long> {
    List<Reclamation> findByCitoyenId(Long id);
    List<Reclamation> findByStatut(StatutReclamation statut);
    List<Reclamation> findByPriorite(Priorite priorite);
    @Query("SELECT COUNT(r) FROM Reclamation r WHERE r.citoyen.id = :id AND r.statut = :statut")
    long countByCitoyenAndStatut(@Param("id") Long id, @Param("statut") StatutReclamation statut);

    // Tu peux aussi ajouter celle-ci pour le total
    long countByCitoyenId(Long id);
    @Query("""
        SELECT COUNT(a) FROM Affectation a
        WHERE a.agent.id = :agentId
        AND a.reclamation.statut = :statut
    """)
    Long countAffectationsParStatut(
            @Param("agentId") Long agentId,
            @Param("statut") StatutReclamation statut
    );


}