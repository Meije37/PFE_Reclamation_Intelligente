package com.pfe.backend.repository;

import com.pfe.backend.entity.CategorieReclamation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategorieReclamationRepository extends JpaRepository<CategorieReclamation, Long> {
}