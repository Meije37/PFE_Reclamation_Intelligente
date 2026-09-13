package com.pfe.backend.repository;

import com.pfe.backend.entity.Notification;
import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.entity.enums.EtatNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByDestinataireOrderByDateEnvoiDesc(Utilisateur destinataire);

    long countByDestinataireAndLuFalse(Utilisateur destinataire);
    // Toutes les notifications d'un utilisateur (triées par date desc)
    List<Notification> findByDestinataireOrderByDateEnvoiDesc(Utilisateur destinataire);

    // Notifications non lues (EN_ATTENTE) d'un utilisateur
    List<Notification> findByDestinataireAndEtat(Utilisateur destinataire, EtatNotification etat);

    // Compter les non lues
    long countByDestinataireAndEtat(Utilisateur destinataire, EtatNotification etat);
}