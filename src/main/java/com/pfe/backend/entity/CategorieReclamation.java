package com.pfe.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CategorieReclamation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idCategorie;

    private String nom;
    private String description;
    private Integer prioriteParDefaut;

    @OneToMany(mappedBy = "categorie")
    private List<Reclamation> reclamations;
}
