package com.pfe.backend.entity;

import com.pfe.backend.entity.enums.Priorite;
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
public class Reclamation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String reference;
    private String titre;
    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime dateCreation = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    private StatutReclamation statut;

    @Enumerated(EnumType.STRING)
    private Priorite priorite;

    private Double scoreUrgence;
    private Boolean estDoublon = false;

    @ManyToOne
    @JoinColumn(name = "citoyen_id")
    private Utilisateur citoyen;

    @OneToOne(cascade = CascadeType.ALL)
    private Localisation localisation;

    @ManyToOne
    private CategorieReclamation categorie;
}
