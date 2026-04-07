package com.pfe.backend.entity;

import com.pfe.backend.entity.enums.TypeMedia;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Media {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nomFichier;
    private String url; // Chemin vers le stockage (ex: /uploads/img1.jpg)

    @Enumerated(EnumType.STRING)
    private TypeMedia type; // PHOTO ou VIDEO

    private LocalDateTime dateUpload = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "reclamation_id")
    private Reclamation reclamation;

    @OneToMany(mappedBy = "media", cascade = CascadeType.ALL)
    private List<AnalyseIA> analyses;
}