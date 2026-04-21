package com.pfe.backend.repository;

import com.pfe.backend.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {

    Optional<Zone> findByNom(String nom);

    boolean existsByNom(String nom);
}