package com.pfe.backend.entity;

import com.pfe.backend.entity.enums.ModeAffectation;
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
public class Affectation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAffectation;
    private LocalDateTime dateAffectation = LocalDateTime.now();
    private String commentaire;

    @Enumerated(EnumType.STRING)
    private ModeAffectation modeAffectation; // AUTOMATIQUE ou MANUELLE

    @ManyToOne
    @JoinColumn(name = "reclamation_id")
    private Reclamation reclamation;

    @ManyToOne
    @JoinColumn(name = "agent_id")
    private Utilisateur agent;
}