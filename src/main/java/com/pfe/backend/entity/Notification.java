package com.pfe.backend.entity;

import com.pfe.backend.entity.enums.EtatNotification;
import com.pfe.backend.entity.enums.TypeNotification;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idNotification;
    private String titre;
    private String message;
    @Enumerated(EnumType.STRING)
    private TypeNotification type;
    @Enumerated(EnumType.STRING)
    private EtatNotification etat;
    private LocalDateTime dateEnvoi;

    private boolean lu = false;

    // Nullable : certaines notifications (rares) peuvent ne concerner aucune
    // réclamation précise. Simple Long (pas de relation JPA) car on n'a besoin
    // que de l'ID pour permettre au client (web/mobile) de naviguer directement
    // vers la réclamation concernée au clic sur la notification.
    private Long reclamationId;

    @ManyToOne
    private Utilisateur destinataire;
}