package com.pfe.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommentaireRequestDTO {
    private String contenu;

    /** "PUBLIC" ou "INTERNE". Ignoré et forcé à PUBLIC si l'auteur est un citoyen. */
    private String visibilite;
}