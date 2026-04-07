package com.pfe.backend.entity;

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
public class Vote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idVote;
    private LocalDateTime dateVote = LocalDateTime.now();
    private Integer valeur; // 1 pour un vote positif

    @ManyToOne
    private Reclamation reclamation;
    @ManyToOne
    private Utilisateur citoyen;
}
