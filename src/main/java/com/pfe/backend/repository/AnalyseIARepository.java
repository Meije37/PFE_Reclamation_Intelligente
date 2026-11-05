package com.pfe.backend.repository;

import com.pfe.backend.entity.AnalyseIA;
import com.pfe.backend.entity.enums.TypeAnalyseIA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalyseIARepository extends JpaRepository<AnalyseIA, Long> {
    // Trouver les analyses avec un faible score de confiance pour validation humaine
    List<AnalyseIA> findByScoreConfianceLessThan(Double seuil);

    /** Analyses d'une réclamation d'un type donné, la plus récente d'abord. */
    List<AnalyseIA> findByReclamation_IdAndTypeAnalyseOrderByDateAnalyseDesc(Long reclamationId, TypeAnalyseIA type);
}
