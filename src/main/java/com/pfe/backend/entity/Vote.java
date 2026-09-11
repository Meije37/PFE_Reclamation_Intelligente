package com.pfe.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
// Empêche en base même 2 votes du même citoyen sur la même réclamation,
// en complément de la vérification applicative dans VoteService (qui reste
// nécessaire pour le comportement "toggle", mais ne suffit pas seule en
// cas de 2 requêtes quasi simultanées sous isolation READ_COMMITTED).
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_vote_citoyen_reclamation",
        columnNames = {"citoyen_id", "reclamation_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Vote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idVote;
    private LocalDateTime dateVote = LocalDateTime.now();
    private Integer valeur; // 1 pour un vote positif

    @ManyToOne
    @JoinColumn(name = "reclamation_id")
    private Reclamation reclamation;
    @ManyToOne
    @JoinColumn(name = "citoyen_id")
    private Utilisateur citoyen;
}