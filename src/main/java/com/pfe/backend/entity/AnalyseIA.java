package com.pfe.backend.entity;

import com.pfe.backend.entity.enums.TypeAnalyseIA;
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
public class AnalyseIA {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAnalyse;

    @Enumerated(EnumType.STRING)
    private TypeAnalyseIA typeAnalyse;

    @Column(columnDefinition = "TEXT")
    private String resultat; // JSON ou texte brut du résultat

    private Double scoreConfiance;
    private LocalDateTime dateAnalyse = LocalDateTime.now();

    private String modeleUtilise; // Nom du modèle (ex: "ResNet-50", "GPT-4")
    private Boolean doublonDetecte = false;

    @ManyToOne
    @JoinColumn(name = "media_id")
    private Media media;

    @ManyToOne
    @JoinColumn(name = "reclamation_id")
    private Reclamation reclamation;
}
