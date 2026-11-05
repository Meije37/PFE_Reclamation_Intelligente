package com.pfe.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.util.List;
import com.pfe.backend.entity.Service;

@Entity
@Table(name = "categorie_reclamation")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CategorieReclamation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idCategorie;

    private String nom;
    private String description;
    private Integer prioriteParDefaut;

    /**
     * Familles IA choisies À LA MAIN par l'admin (facultatif), séparées par des virgules,
     * ex. « VOIRIE_INFRASTRUCTURES,ELECTRICITE_ECLAIRAGE ». Vide = déduites du service responsable.
     */
    @Column(name = "familles_ia", length = 200)
    private String famillesIA;

    /** Ancienne colonne (une seule famille) : lue seulement si famillesIA est vide. */
    @JsonIgnore
    @Column(length = 40)
    private String familleIA;

    /** Familles choisies à la main (vide si aucune). */
    public java.util.List<String> famillesIAManuelles() {
        String brut = famillesIA != null && !famillesIA.isBlank() ? famillesIA : familleIA;
        if (brut == null || brut.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(brut.split(","))
                .map(String::trim).filter(f -> !f.isEmpty()).toList();
    }

    /** Familles déduites du nom du service responsable (ex. « Voirie et Infrastructures » → VOIRIE_INFRASTRUCTURES). */
    public java.util.List<String> famillesIADeduites() {
        return serviceResponsable != null ? FamillesIA.depuisNomService(serviceResponsable.getNom()) : java.util.List.of();
    }

    /** Familles utilisées par l'IA : celles choisies à la main, sinon celles déduites du service. */
    public java.util.List<String> listeFamillesIA() {
        java.util.List<String> manuelles = famillesIAManuelles();
        return manuelles.isEmpty() ? famillesIADeduites() : manuelles;
    }

    /** Vrai si cette catégorie couvre la famille donnée. */
    public boolean couvreFamille(String famille) {
        return famille != null && listeFamillesIA().contains(famille);
    }
    @JsonIgnore
    @OneToMany(mappedBy = "categorie")
    private List<Reclamation> reclamations;
    @ManyToOne(fetch = FetchType.EAGER)

    @JoinColumn(name = "service_id", nullable = true)
    @JsonIgnoreProperties({"services", "interventions"})
    private Service serviceResponsable;


}
