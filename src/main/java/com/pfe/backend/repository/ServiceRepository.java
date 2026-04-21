package com.pfe.backend.repository;

import com.pfe.backend.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {

    Optional<Service> findByNom(String nom);

    boolean existsByNom(String nom);

    boolean existsByEmailService(String emailService);
}