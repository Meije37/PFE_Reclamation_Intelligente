package com.pfe.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class NotificationResponseDTO {
    private Long id;
    private String titre;
    private String message;
    private String type;
    private boolean lu;
    private LocalDateTime dateEnvoi;
    private Long reclamationId; // null si la notification ne concerne aucune réclamation précise
}