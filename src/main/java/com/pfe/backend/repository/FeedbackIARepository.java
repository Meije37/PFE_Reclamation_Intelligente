package com.pfe.backend.repository;

import com.pfe.backend.entity.FeedbackIA;
import com.pfe.backend.entity.enums.TypeFeedbackIA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackIARepository extends JpaRepository<FeedbackIA, Long> {

    boolean existsByReclamation_IdAndTypeDecisionIn(Long reclamationId, List<TypeFeedbackIA> types);

    long countByTypeDecision(TypeFeedbackIA type);

    List<FeedbackIA> findAllByOrderByDateDecisionAsc();

    /** Décisions d'une famille (doublons, périmètre ou priorité), pour les exports dataset_v2. */
    List<FeedbackIA> findByTypeDecisionInOrderByDateDecisionAsc(List<TypeFeedbackIA> types);
}
