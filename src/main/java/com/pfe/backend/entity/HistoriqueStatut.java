package com.pfe.backend.entity;

import com.pfe.backend.entity.enums.StatutReclamation;
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
public class HistoriqueStatut {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idHistorique;
    @Enumerated(EnumType.STRING)
    private StatutReclamation ancienStatut;
    @Enumerated(EnumType.STRING)
    private StatutReclamation nouveauStatut;
    private LocalDateTime dateChangement = LocalDateTime.now();
    private String commentaire;

    @ManyToOne
    private Reclamation reclamation;
}
