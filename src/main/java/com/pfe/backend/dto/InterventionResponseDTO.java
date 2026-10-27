package com.pfe.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class InterventionResponseDTO {
    private Long id;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private String statut;
    private String resultat;
    private Double coutEstime;
    private String serviceNom;
    private String agentNom;
    /** Vrai si l'utilisateur connecté peut changer le statut (agent propriétaire ou admin). */
    private boolean peutModifier;
    /** Vrai si l'utilisateur peut annuler (propriétaire, admin, ou planificateur tant que PLANIFIEE). */
    private boolean peutAnnuler;
    private String planifieParNom;
    private Long agentId;
    private Long serviceId;
    /** Vrai si l'utilisateur peut transférer l'intervention (exécutant, planificateur ou admin, tant que PLANIFIEE). */
    private boolean peutTransferer;
    /** Vrai si l'agent exécutant a été choisi par le système. */
    private boolean affectationAutomatique;
    /** Heure réelle de démarrage (null si l'intervention n'a pas démarré ou est antérieure à ce champ). */
    private LocalDateTime dateDemarrage;
}