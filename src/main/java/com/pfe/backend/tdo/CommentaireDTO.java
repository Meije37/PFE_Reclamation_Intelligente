package com.pfe.backend.tdo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentaireDTO {
    private String contenu;
    private String auteurNom;
    private LocalDateTime date;
    private Long reclamationId;
}