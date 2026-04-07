package com.pfe.backend.repository;

import com.pfe.backend.entity.Reclamation;
import com.pfe.backend.entity.enums.StatutReclamation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReclamationRepository extends JpaRepository<Reclamation, Long> {
    List<Reclamation> findByCitoyenId(Long id);
    List<Reclamation> findByStatut(StatutReclamation statut);
}