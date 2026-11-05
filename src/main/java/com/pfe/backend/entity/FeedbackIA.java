package com.pfe.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pfe.backend.entity.enums.TypeFeedbackIA;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Décision d'un administrateur sur une proposition de l'IA (Human-in-the-Loop).
 *
 * Chaque ligne est un exemple étiqueté par un humain : l'ensemble est exporté en CSV
 * (dataset_v2) pour mesurer la précision réelle de l'IA et recalibrer ses seuils.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class FeedbackIA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TypeFeedbackIA typeDecision;

    /** Réclamation signalée comme doublon potentiel par l'IA. */
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "reclamation_id")
    private Reclamation reclamation;

    /** Réclamation d'origine proposée par l'IA (celle à laquelle elle ressemble). */
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "reclamation_origine_id")
    private Reclamation reclamationOrigine;

    /** Ce que l'IA avait mesuré au moment de la proposition. */
    private Double similarite;
    private Double distanceM;

    // ── Périmètre et priorité : proposition de l'IA face à la décision humaine ──
    /** Ex. « MUNICIPAL », « HORS_PERIMETRE », « HAUTE ». */
    @Column(length = 40)
    private String valeurIA;
    /** Ex. « MUNICIPAL », « HORS_PERIMETRE », « CRITIQUE ». */
    @Column(length = 40)
    private String valeurHumaine;
    /** Confiance de l'IA dans sa proposition (probabilité municipal, ou probabilité de la priorité). */
    private Double confianceIA;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "auteur_id")
    private Utilisateur auteur;

    private LocalDateTime dateDecision = LocalDateTime.now();

    @Column(length = 500)
    private String commentaire;
}
