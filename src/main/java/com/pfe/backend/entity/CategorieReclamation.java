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
    @JsonIgnore
    @OneToMany(mappedBy = "categorie")
    private List<Reclamation> reclamations;
    @ManyToOne(fetch = FetchType.EAGER)

    @JoinColumn(name = "service_id", nullable = true)
    @JsonIgnoreProperties({"services", "interventions"})
    private Service serviceResponsable;


}
