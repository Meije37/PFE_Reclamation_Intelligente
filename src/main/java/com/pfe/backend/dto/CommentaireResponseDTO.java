package com.pfe.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class CommentaireResponseDTO {
    private Long id;
    private String contenu;
    private String visibilite;
    private LocalDateTime dateCommentaire;
    private String auteurNom;
    private String auteurRole;
    private Long auteurId;
}