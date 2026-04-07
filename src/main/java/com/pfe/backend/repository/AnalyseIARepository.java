package com.pfe.backend.repository;

import com.pfe.backend.entity.AnalyseIA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalyseIARepository extends JpaRepository<AnalyseIA, Long> {
    // Trouver les analyses avec un faible score de confiance pour validation humaine
    List<AnalyseIA> findByScoreConfianceLessThan(Double seuil);
}
