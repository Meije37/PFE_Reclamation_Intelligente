package com.pfe.backend.service;

import com.pfe.backend.exception.FichierInvalideException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
        byte[] contenu = lireContenu(file);
        validerFichier(file, contenu);

        try {
            Path root = Paths.get(uploadPath);
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }

            String extension = extraireExtension(file.getOriginalFilename());
            String fileName = UUID.randomUUID() + extension;

            Files.write(root.resolve(fileName), contenu);

            return fileName;
        } catch (IOException e) {
            throw new FichierInvalideException(
                    "Erreur lors de l'enregistrement du fichier : " + e.getMessage());
        }
    }

    private byte[] lireContenu(MultipartFile file) {
        try {
            return file != null ? file.getBytes() : new byte[0];
        } catch (IOException e) {
            throw new FichierInvalideException(
                    "Impossible de lire le fichier envoyé : " + e.getMessage());
        }
    }

    private void validerFichier(MultipartFile file, byte[] contenu) {
        if (file == null || file.isEmpty()) {
            throw new FichierInvalideException("Le fichier est vide ou absent.");
        }

        if (file.getSize() > TAILLE_MAX_OCTETS) {
            throw new FichierInvalideException(
                    "Le fichier dépasse la taille maximale autorisée (5 Mo).");
        }

        // 1) Content-Type déclaré par le client : première barrière, mais
        //    facilement falsifiable (le client choisit cette valeur), donc
        //    insuffisante seule.
        String contentType = file.getContentType();
        if (contentType == null || !TYPES_AUTORISES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new FichierInvalideException(
                    "Type de fichier non autorisé. Formats acceptés : JPEG, PNG, WEBP, HEIC.");
        }

        // 2) Magic bytes : signature binaire réelle des premiers octets du
        //    fichier, que le client ne peut pas simplement déclarer — il
        //    faudrait qu'il falsifie le contenu binaire lui-même. Empêche
        //    par exemple un exécutable renommé en ".jpg" avec un
        //    Content-Type "image/jpeg" mensonger.
        if (!signatureCorrespondAUneImage(contenu)) {
            throw new FichierInvalideException(
                    "Le contenu du fichier ne correspond à aucun format d'image reconnu "
                            + "(JPEG, PNG, WEBP, HEIC) — le fichier semble corrompu ou n'est pas une image.");
        }
    }

    /**
     * Vérifie la signature binaire ("magic bytes") des premiers octets du
     * fichier, indépendamment du nom de fichier ou du Content-Type déclaré.
     */
    private boolean signatureCorrespondAUneImage(byte[] c) {
        if (estJPEG(c) || estPNG(c) || estWEBP(c) || estHEIC(c)) {
            return true;
        }
        return false;
    }

    private boolean estJPEG(byte[] c) {
        // FF D8 FF
        return c.length >= 3
                && (c[0] & 0xFF) == 0xFF
                && (c[1] & 0xFF) == 0xD8
                && (c[2] & 0xFF) == 0xFF;
    }

    private boolean estPNG(byte[] c) {
        // 89 50 4E 47 0D 0A 1A 0A
        int[] signature = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (c.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((c[i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }

    private boolean estWEBP(byte[] c) {
        // Conteneur RIFF : "RIFF" (0-3) .... "WEBP" (8-11)
        if (c.length < 12) return false;
        String riff = new String(c, 0, 4, StandardCharsets.US_ASCII);
        String webp = new String(c, 8, 4, StandardCharsets.US_ASCII);
        return riff.equals("RIFF") && webp.equals("WEBP");
    }

    private boolean estHEIC(byte[] c) {
        // Conteneur ISOBMFF : boîte "ftyp" (4-7) suivie d'une marque de
        // compatibilité HEIC/HEIF (8-11).
        if (c.length < 12) return false;
        String boite = new String(c, 4, 4, StandardCharsets.US_ASCII);
        if (!boite.equals("ftyp")) return false;
        String marque = new String(c, 8, 4, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
        return List.of("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1")
                .contains(marque);
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