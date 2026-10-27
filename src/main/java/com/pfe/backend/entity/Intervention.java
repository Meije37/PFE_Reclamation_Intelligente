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

    // Heure réelle de démarrage (clic sur « Démarrer »). dateDebut, elle, est la date de planification.
    // Nulle pour les interventions antérieures à l'ajout de ce champ.
    private LocalDateTime dateDemarrage;
    @Column(length = 1000)
    private String resultat;
    private Double coutEstime;

    @Enumerated(EnumType.STRING)
    private StatutIntervention statutIntervention;

    @ManyToOne
    private Reclamation reclamation;

    @ManyToOne
    private Service service;

    // L'agent qui a effectué (ou va effectuer) l'intervention terrain.
    // Distinct de Affectation.agent : une affectation dit "qui est responsable
    // du dossier", une intervention documente "ce qui a été fait, quand, et
    // combien ça a coûté" — plusieurs interventions peuvent suivre une même
    // affectation (ex: un premier passage, puis un second pour finaliser).
    @ManyToOne
    @JoinColumn(name = "agent_id")
    private Utilisateur agent;

    // Qui a planifié l'intervention (peut être différent de l'agent exécutant).
    // Sert à lui permettre d'annuler sa planification tant qu'elle n'a pas démarré.
    // Nul pour les interventions créées avant l'ajout de ce champ.
    @ManyToOne
    @JoinColumn(name = "planifie_par_id")
    private Utilisateur planifiePar;

    // Vrai si l'agent exécutant a été choisi par le système (et non par l'utilisateur).
    // Wrapper Boolean : les anciennes lignes de la table ont une valeur nulle.
    private Boolean affectationAutomatique;
}