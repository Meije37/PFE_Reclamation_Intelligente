package com.pfe.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String expediteur;

    /**
     * Envoie le code OTP par email. En cas d'échec (SMTP non configuré,
     * pas de connexion internet, etc.), le code est tout de même affiché
     * dans les logs serveur pour permettre de continuer les tests en
     * développement sans configuration mail réelle.
     */
    public void envoyerCodeOtp(String destinataire, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(expediteur);
            message.setTo(destinataire);
            message.setSubject("Code de vérification - Réinitialisation du mot de passe");
            message.setText(
                    "Bonjour,\n\n" +
                            "Voici votre code de vérification : " + code + "\n\n" +
                            "Ce code expire dans 10 minutes. Si vous n'êtes pas à l'origine de cette " +
                            "demande, ignorez simplement cet email.\n\n" +
                            "Plateforme de Réclamations Citoyennes"
            );
            mailSender.send(message);
            log.info("Email OTP envoyé à {}", destinataire);
        } catch (Exception e) {
            log.warn("Échec de l'envoi de l'email OTP à {} ({}). " +
                            "Code affiché ici pour continuer les tests : {}",
                    destinataire, e.getMessage(), code);
        }
    }

    /**
     * Envoie un email générique (utilisé notamment par les notifications :
     * changement de statut, assignation d'agent, etc.). Même logique de
     * tolérance aux pannes que envoyerCodeOtp : en cas d'échec, on logue
     * et on continue — l'échec d'un email ne doit jamais faire échouer
     * l'action métier qui l'a déclenché (ex: changer un statut).
     */
    public void envoyerEmailNotification(String destinataire, String sujet, String corps) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(expediteur);
            message.setTo(destinataire);
            message.setSubject(sujet);
            message.setText(corps);
            mailSender.send(message);
            log.info("Email de notification envoyé à {}", destinataire);
        } catch (Exception e) {
            log.warn("Échec de l'envoi de l'email de notification à {} ({}).",
                    destinataire, e.getMessage());
        }
    }
}