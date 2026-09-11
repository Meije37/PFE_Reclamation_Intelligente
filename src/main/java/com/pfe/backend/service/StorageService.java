package com.pfe.backend.service;

import com.pfe.backend.exception.FichierInvalideException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class StorageService {

    @Value("${upload.path}")
    private String uploadPath;

    // Types MIME autorisés pour les photos jointes aux réclamations
    private static final List<String> TYPES_AUTORISES = List.of(
            "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"
    );

    private static final long TAILLE_MAX_OCTETS = 5L * 1024 * 1024; // 5 Mo (cohérent avec application.properties)

    public String store(MultipartFile file) {
        validerFichier(file);

        try {
            Path root = Paths.get(uploadPath);
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }

            String extension = extraireExtension(file.getOriginalFilename());
            String fileName = UUID.randomUUID() + extension;

            Files.copy(file.getInputStream(), root.resolve(fileName));

            return fileName;
        } catch (IOException e) {
            throw new FichierInvalideException(
                    "Erreur lors de l'enregistrement du fichier : " + e.getMessage());
        }
    }

    private void validerFichier(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FichierInvalideException("Le fichier est vide ou absent.");
        }

        if (file.getSize() > TAILLE_MAX_OCTETS) {
            throw new FichierInvalideException(
                    "Le fichier dépasse la taille maximale autorisée (5 Mo).");
        }

        String contentType = file.getContentType();
        if (contentType == null || !TYPES_AUTORISES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new FichierInvalideException(
                    "Type de fichier non autorisé. Formats acceptés : JPEG, PNG, WEBP, HEIC.");
        }
    }

    /**
     * Extrait l'extension du nom de fichier original de façon sûre.
     * Retourne une chaîne vide si le nom est absent ou ne contient pas de point,
     * plutôt que de lever une exception (comportement précédent).
     */
    private String extraireExtension(String originalFileName) {
        if (originalFileName == null) {
            return "";
        }
        int lastDot = originalFileName.lastIndexOf(".");
        if (lastDot < 0 || lastDot == originalFileName.length() - 1) {
            return "";
        }
        return originalFileName.substring(lastDot);
    }
}