package com.pfe.backend.repository;

import com.pfe.backend.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoteRepository extends JpaRepository<Vote, Long> {

    Optional<Vote> findByReclamation_IdAndCitoyen_Id(Long reclamationId, Long citoyenId);

    // Comptage groupé pour une page de réclamations en une seule requête
    // (évite une requête de comptage par réclamation affichée).
    @Query("SELECT v.reclamation.id, COUNT(v) FROM Vote v " +
            "WHERE v.reclamation.id IN :ids GROUP BY v.reclamation.id")
    List<Object[]> countByReclamationIds(@Param("ids") List<Long> ids);

    // Parmi ces réclamations, lesquelles ce citoyen a-t-il déjà votées ?
    @Query("SELECT v.reclamation.id FROM Vote v " +
            "WHERE v.reclamation.id IN :ids AND v.citoyen.id = :citoyenId")
    List<Long> findReclamationIdsVotedBy(@Param("ids") List<Long> ids,
                                         @Param("citoyenId") Long citoyenId);

    long countByReclamation_Id(Long reclamationId);
}