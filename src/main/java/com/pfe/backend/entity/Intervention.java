package com.pfe.backend.entity;

import com.pfe.backend.entity.enums.StatutIntervention;
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
public class Intervention {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idIntervention;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private String resultat;
    private Double coutEstime;

    @Enumerated(EnumType.STRING)
    private StatutIntervention statutIntervention;

    @ManyToOne
    private Reclamation reclamation;

    @ManyToOne
    private Service service;
}
