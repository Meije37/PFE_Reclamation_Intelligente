package com.pfe.backend.service;

import com.pfe.backend.dto.NotificationResponseDTO;
import com.pfe.backend.entity.Notification;
import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.entity.enums.EtatNotification;
import com.pfe.backend.entity.enums.TypeNotification;
import com.pfe.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Crée une notification in-app (toujours) ET tente un email (best-effort).
     * Appelée depuis ReclamationService/InterventionService/CommentaireService
     * lors d'un événement lié à une réclamation — jamais directement depuis
     * un contrôleur.
     *
     * @deprecated préférer la surcharge avec reclamationId quand une
     * réclamation est concernée, pour permettre au client de naviguer
     * directement dessus au clic sur la notification.
     */
    @Deprecated
    public void creer(Utilisateur destinataire, String titre, String message) {
        creer(destinataire, titre, message, null);
    }

    public void creer(Utilisateur destinataire, String titre, String message, Long reclamationId) {
        Notification notification = new Notification();
        notification.setDestinataire(destinataire);
        notification.setTitre(titre);
        notification.setMessage(message);
        notification.setType(TypeNotification.PUSH);
        notification.setEtat(EtatNotification.ENVOYEE);
        notification.setDateEnvoi(LocalDateTime.now());
        notification.setLu(false);
        notification.setReclamationId(reclamationId);
        notificationRepository.save(notification);

        // Poussée temps réel via STOMP, best-effort : si l'utilisateur n'a
        // pas de session WebSocket ouverte, ce message est simplement perdu
        // (il reste consultable via GET /api/notifications au prochain login).
        if (destinataire.getEmail() != null) {
            messagingTemplate.convertAndSendToUser(
                    destinataire.getEmail(),
                    "/queue/notifications",
                    versDTO(notification)
            );
        }

        // Envoi email en plus, en best-effort : un échec d'email ne doit
        // jamais empêcher la notification in-app d'exister.
        if (destinataire.getEmail() != null) {
            emailService.envoyerEmailNotification(
                    destinataire.getEmail(),
                    titre,
                    message + "\n\n— Plateforme de Réclamations Citoyennes"
            );
        }
    }

    public List<NotificationResponseDTO> listerPourUtilisateur(Utilisateur utilisateur) {
        return notificationRepository.findByDestinataireOrderByDateEnvoiDesc(utilisateur)
                .stream()
                .map(this::versDTO)
                .collect(Collectors.toList());
    }

    public long compterNonLues(Utilisateur utilisateur) {
        return notificationRepository.countByDestinataireAndLuFalse(utilisateur);
    }

    @Transactional
    public void marquerCommeLue(Long notificationId, Utilisateur utilisateur) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification introuvable."));

        if (!notification.getDestinataire().getId().equals(utilisateur.getId())) {
            throw new RuntimeException("Cette notification ne vous appartient pas.");
        }

        notification.setLu(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void marquerToutesCommeLues(Utilisateur utilisateur) {
        List<Notification> notifications =
                notificationRepository.findByDestinataireOrderByDateEnvoiDesc(utilisateur);

        notifications.forEach(n -> n.setLu(true));
        notificationRepository.saveAll(notifications);
    }

    private NotificationResponseDTO versDTO(Notification n) {
        return new NotificationResponseDTO(
                n.getIdNotification(),
                n.getTitre(),
                n.getMessage(),
                n.getType() != null ? n.getType().name() : null,
                n.isLu(),
                n.getDateEnvoi(),
                n.getReclamationId()
        );
    }
}