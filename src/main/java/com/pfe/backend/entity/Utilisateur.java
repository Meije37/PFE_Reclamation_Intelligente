package com.pfe.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pfe.backend.entity.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "utilisateurs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String motDePasse;

    @Column(nullable = false, unique = true)
    private String telephone;

    private Boolean actif = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.CITOYEN;

    @JsonIgnore
    @OneToMany(mappedBy = "citoyen")
    private List<Reclamation> reclamations;
}