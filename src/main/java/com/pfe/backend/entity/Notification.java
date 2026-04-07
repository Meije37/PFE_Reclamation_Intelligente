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

    @ManyToOne
    private Utilisateur destinataire;
}