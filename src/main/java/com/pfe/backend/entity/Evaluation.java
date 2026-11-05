package com.pfe.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Avis du citoyen sur le traitement de SA réclamation, donné une fois qu'elle est résolue.
 *
 * Règles (vérifiées dans EvaluationService) :
 *  - note entière de 1 à 5, commentaire facultatif (500 caractères max) ;
 *  - une seule évaluation par réclamation (contrainte d'unicité en base en plus du contrôle applicatif) ;
 *  - l'agent est mémorisé au moment de l'avis, pour calculer la satisfaction par agent
 *    même si la réclamation est réaffectée plus tard.
 */
@Entity
@Table(name = "evaluation",
        uniqueConstraints = @UniqueConstraint(name = "uk_evaluation_reclamation", columnNames = "reclamation_id"))
@Getter
@Setter
@NoArgsConstructor
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer note;

    @Column(length = 500)
    private String commentaire;

    private LocalDateTime dateEvaluation = LocalDateTime.now();

    @JsonIgnore
    @OneToOne
    @JoinColumn(name = "reclamation_id", nullable = false)
    private Reclamation reclamation;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "citoyen_id")
    private Utilisateur citoyen;

    /** Agent responsable du dossier au moment de l'avis (peut être null). */
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "agent_id")
    private Utilisateur agent;
}
