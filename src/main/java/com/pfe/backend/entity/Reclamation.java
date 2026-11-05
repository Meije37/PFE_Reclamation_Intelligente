package com.pfe.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pfe.backend.entity.enums.Priorite;
import com.pfe.backend.entity.enums.StatutReclamation;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

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

    // ── Ce que l'IA a proposé au dépôt (gardé tel quel, pour mesurer l'IA face aux humains) ──
    /** Probabilité « relève de la commune » donnée par le modèle is_municipal (null si IA injoignable). */
    private Double probabiliteMunicipal;
    /** Vrai tant qu'un humain n'a pas tranché : IA « hors périmètre » ou confiance < 90 %. */
    private Boolean perimetreAVerifier = false;
    /** Priorité proposée par l'IA (la colonne « priorite » peut ensuite être corrigée par un humain). */
    @Enumerated(EnumType.STRING)
    private Priorite prioriteIA;
    /** Vrai si la priorité a été relevée à CRITIQUE par la règle « sécurité d'abord ». */
    private Boolean alerteCritiqueIA;
    /** Type d'incident reconnu par l'IA (ex. coupure_eau), ou null. */
    private String incidentIA;
    /** Vrai quand un agent ou un admin a confirmé ou corrigé la priorité proposée. */
    private Boolean prioriteValidee = false;
    /** Probabilité de la priorité la plus probable : < 0,5 = l'IA hésite (« IA peu sûre »). */
    private Double confiancePrioriteIA;

    /** Catégorie que l'IA suggère d'après le texte, quand elle diffère de celle choisie par le citoyen. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne
    @JoinColumn(name = "categorie_suggeree_id")
    private CategorieReclamation categorieSuggereeIA;
    /** Famille déduite du texte par l'IA (ex. EAU_ASSAINISSEMENT), gardée pour mesurer l'IA. */
    @Column(length = 40)
    private String familleSuggereeIA;
    /** Vrai tant qu'un humain n'a pas tranché entre la catégorie du citoyen et celle suggérée. */
    private Boolean categorieAVerifier = false;

    public Long getCategorieSuggereeId() {
        return categorieSuggereeIA != null ? categorieSuggereeIA.getIdCategorie() : null;
    }

    public String getCategorieSuggereeNom() {
        return categorieSuggereeIA != null ? categorieSuggereeIA.getNom() : null;
    }

    /**
     * Réclamation d'origine, renseignée quand un administrateur CONFIRME le doublon proposé par l'IA.
     * Non sérialisée (évite les boucles JSON) : seule sa référence est exposée, via getDoublonDeReference().
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne
    @JoinColumn(name = "doublon_de_id")
    private Reclamation doublonDe;

    /** Référence de la réclamation d'origine (ex. REC-1A2B3C4D), ou null. */
    public String getDoublonDeReference() {
        return doublonDe != null ? doublonDe.getReference() : null;
    }

    @ManyToOne
    @JoinColumn(name = "citoyen_id")
    private Utilisateur citoyen;

    @OneToOne(cascade = CascadeType.ALL)
    private Localisation localisation;

    @ManyToOne
    private CategorieReclamation categorie;
    @JsonIgnoreProperties("reclamation")
    @OneToMany(mappedBy = "reclamation", cascade = CascadeType.ALL)
    private List<Media> medias;

    // Champ non persisté : rempli à la volée par AdminReclamationController
    // (via VoteRepository) juste avant de renvoyer la réponse JSON, pour que
    // l'admin voie combien de citoyens soutiennent cette réclamation, sans
    // stocker cette donnée redondante en base (elle est déjà dans la table
    // Vote, ceci n'est qu'un raccourci d'affichage).
    @Transient
    private Long nombreVotes;
}