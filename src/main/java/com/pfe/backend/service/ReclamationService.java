package com.pfe.backend.service;

import com.pfe.backend.dto.ReclamationRequestDTO;
import com.pfe.backend.entity.*;
import com.pfe.backend.entity.enums.Priorite;
import com.pfe.backend.entity.enums.StatutReclamation;
import com.pfe.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReclamationService {

    private final ReclamationRepository reclamationRepository;
    private final CategorieReclamationRepository categorieRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final LocalisationRepository localisationRepository;
    private final RestTemplate restTemplate;

    @Transactional
    public Reclamation creerReclamation(ReclamationRequestDTO dto, String emailCitoyen) {
        // Validation Citoyen
        Utilisateur citoyen = utilisateurRepository.findByEmail(emailCitoyen)
                .orElseThrow(() -> new RuntimeException("Erreur : Citoyen non identifié."));

        // Validation Catégorie
        CategorieReclamation categorie = categorieRepository.findById(dto.getCategorieId())
                .orElseThrow(() -> new RuntimeException("Erreur : La catégorie spécifiée n'existe pas."));
        // 3. Créer et sauvegarder la localisation (idLocalisation sera généré)
        Localisation loc = new Localisation();
        loc.setLatitude(dto.getLatitude());
        loc.setLongitude(dto.getLongitude());
        loc.setAdresse(dto.getAdresse());
        loc.setQuartier(dto.getQuartier());
        loc.setVille(dto.getVille());
        Localisation savedLoc = localisationRepository.save(loc);

        // 4. Créer la réclamation avec ta structure exacte
        Reclamation rec = new Reclamation();
        rec.setTitre(dto.getTitre());
        rec.setDescription(dto.getDescription());
        rec.setCitoyen(citoyen);
        rec.setCategorie(categorie);
        rec.setLocalisation(savedLoc);
        rec.setDateCreation(LocalDateTime.now());
        rec.setStatut(StatutReclamation.OUVERTE); // Statut initial de ton Enum



        // Génération de la référence unique
        rec.setReference("REC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        // Simulation IA : Calcul du score d'urgence basé sur les mots-clés
        rec.setScoreUrgence(analyserUrgenceIA(dto.getDescription()));

        // Détermination de la priorité automatique
        rec.setPriorite(determinerPriorite(rec.getScoreUrgence(), categorie.getPrioriteParDefaut()));

        // Détection de doublon (simulée)
        rec.setEstDoublon(false);

        return reclamationRepository.save(rec);
    }

    // Méthodes pour le Dashboard Citoyen
    public List<Reclamation> getReclamationsParEmail(String email) {
        Utilisateur user = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return reclamationRepository.findByCitoyenId(user.getId());
    }

    public Map<String, Long> getStatsCitoyen(String email) {
        Utilisateur user = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        List<Reclamation> mesRecs = reclamationRepository.findByCitoyenId(user.getId());

        return Map.of(
                "total", (long) mesRecs.size(),
                "enCours", mesRecs.stream().filter(r -> r.getStatut() == StatutReclamation.EN_COURS).count(),
                "resolues", mesRecs.stream().filter(r -> r.getStatut() == StatutReclamation.RESOLUE).count()
        );
    }
//

    // --- Fonctions privées de logique "IA" ---

    private Double analyserUrgenceIA(String description) {
        try {
            String url = "http://127.0.0.1:8000/predict-urgency";
            Map<String, String> request = Map.of("text", description);

            // Appel direct au service Python
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            return (Double) response.get("score");
        } catch (Exception e) {
            System.err.println("Erreur de connexion au service IA: " + e.getMessage());
            return 0.5; // Score de secours si Python est éteint
        }
    }

    private Priorite determinerPriorite(Double score, Integer prioriteBase) {
        // Si le score IA est très haut ou si la catégorie est prioritaire par défaut
        if (score > 0.8 || (prioriteBase != null && prioriteBase > 3)) {
            return Priorite.CRITIQUE;
        }
        if (score > 0.5) return Priorite.HAUTE;
        if (score > 0.3) return Priorite.MOYENNE;
        return Priorite.BASSE;
    }
    public Map<String, Long> getCitoyenStats(Long citoyenId) {
        Map<String, Long> stats = new HashMap<>();

        stats.put("total", reclamationRepository.countByCitoyenId(citoyenId));

        stats.put("enCours", reclamationRepository.countByCitoyenAndStatut(
                citoyenId, StatutReclamation.EN_COURS));

        stats.put("resolues", reclamationRepository.countByCitoyenAndStatut(
                citoyenId, StatutReclamation.RESOLUE));

        return stats;
    }
    public Reclamation getReclamationDetaillee(Long id, String email) {
        Reclamation rec = reclamationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Réclamation introuvable."));

        // Sécurité : Vérifier que c'est bien la sienne
        if (!rec.getCitoyen().getEmail().equals(email)) {
            throw new RuntimeException("Accès non autorisé à cette réclamation.");
        }
        return rec;
    }
    @Transactional // IMPORTANT : Assure que toute l'opération est une seule transaction
    public void annulerReclamation(Long id, String email) {
        // 1. On cherche la réclamation
        Reclamation rec = reclamationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Réclamation introuvable."));

        // 2. Vérification de sécurité
        if (!rec.getCitoyen().getEmail().equals(email)) {
            throw new RuntimeException("Vous n'avez pas le droit d'annuler cette réclamation.");
        }

        // 3. Vérification du statut
        if (rec.getStatut() != StatutReclamation.OUVERTE) {
            throw new RuntimeException("Impossible d'annuler une réclamation déjà en cours.");
        }

        // 4. Mise à jour simple
        rec.setStatut(StatutReclamation.ANNULEE);


        // Pas besoin de appeler save() explicitement si c'est @Transactional,
        // mais tu peux le laisser pour être sûr.
        reclamationRepository.save(rec);
        System.out.println("Annulation pour : " + id);
    }
}