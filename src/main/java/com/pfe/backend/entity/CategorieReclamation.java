package com.pfe.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.util.List;

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
}
