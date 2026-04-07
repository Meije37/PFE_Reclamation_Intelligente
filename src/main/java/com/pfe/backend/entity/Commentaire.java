package com.pfe.backend.entity;

import com.pfe.backend.entity.enums.VisibiliteCommentaire;
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
public class Commentaire {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idCommentaire;
    @Column(columnDefinition = "TEXT")
    private String contenu;
    private LocalDateTime dateCommentaire = LocalDateTime.now();
    @Enumerated(EnumType.STRING)
    private VisibiliteCommentaire visibilite;

    @ManyToOne
    private Reclamation reclamation;
    @ManyToOne
    private Utilisateur auteur;
}