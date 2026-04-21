package com.pfe.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class StorageService {

    @Value("${upload.path}")
    private String uploadPath; // Récupère le chemin depuis application.properties

    public String store(MultipartFile file) throws IOException {
        Path root = Paths.get(uploadPath); // Utilise le chemin absolu (C:/...)

        // Créer le dossier s'il n'existe pas
        if (!Files.exists(root)) {
            Files.createDirectories(root);
        }

        String originalFileName = file.getOriginalFilename();
        String extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        String fileName = UUID.randomUUID().toString() + extension;

        // Copie physique du fichier dans C:/PFE_Reclamation_Intelligente/uploads
        Files.copy(file.getInputStream(), root.resolve(fileName));

        return fileName;
    }
}