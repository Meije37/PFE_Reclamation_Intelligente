package com.pfe.backend.repository;

import com.pfe.backend.entity.Notification;
import com.pfe.backend.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByDestinataireOrderByDateEnvoiDesc(Utilisateur destinataire);

    long countByDestinataireAndLuFalse(Utilisateur destinataire);
}