package com.pfe.backend.service;

import com.pfe.backend.dto.ReclamationPubliqueDTO;
import com.pfe.backend.entity.Reclamation;
import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.entity.Vote;
import com.pfe.backend.entity.enums.StatutReclamation;
import com.pfe.backend.exception.AccesRefuseException;
import com.pfe.backend.repository.ReclamationRepository;
import com.pfe.backend.repository.UtilisateurRepository;
import com.pfe.backend.repository.VoteRepository;
import com.pfe.backend.repository.HistoriqueStatutRepository;
import com.pfe.backend.repository.MediaRepository;
import com.pfe.backend.entity.HistoriqueStatut;
import com.pfe.backend.entity.Media;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VoteService {

    private final VoteRepository voteRepository;
    private final ReclamationRepository reclamationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final HistoriqueStatutRepository historiqueStatutRepository;
    private final MediaRepository mediaRepository;

    /**
     * Liste publique des réclamations (tous citoyens confondus), avec le
     * nombre de votes et si LE citoyen connecté a déjà voté pour chacune.
     *
     * @param trierParVotes true = "les plus soutenues" d'abord, false = les plus récentes d'abord
     */
    public Page<ReclamationPubliqueDTO> listerPubliques(
            String emailCitoyenConnecte,
            Long zoneId, Long categorieId, StatutReclamation statut,
            boolean trierParVotes, Pageable pageable) {

        // emailCitoyenConnecte == null : visiteur sans compte (consultation publique).
        Utilisateur moi = trouverUtilisateurOuNull(emailCitoyenConnecte);
        boolean anonyme = moi == null;

        Page<Reclamation> page = trierParVotes
                ? reclamationRepository.findPubliquesTrieesParVotes(zoneId, categorieId, statut, pageable)
                : reclamationRepository.findPubliques(zoneId, categorieId, statut, pageable);

        List<Long> ids = page.getContent().stream().map(Reclamation::getId).toList();

        // 2 requêtes groupées au lieu d'une par réclamation affichée.
        Map<Long, Long> votesParReclamation = voteRepository.countByReclamationIds(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        List<Long> idsDejaVotes = (anonyme || ids.isEmpty())
                ? List.of()
                : voteRepository.findReclamationIdsVotedBy(ids, moi.getId());

        return page.map(r -> new ReclamationPubliqueDTO(
                r.getId(),
                r.getReference(),
                r.getTitre(),
                r.getDescription(),
                r.getStatut() != null ? r.getStatut().name() : null,
                r.getPriorite() != null ? r.getPriorite().name() : null,
                r.getDateCreation(),
                r.getCategorie() != null
                        ? new ReclamationPubliqueDTO.CategorieLegere(r.getCategorie().getNom())
                        : null,
                r.getLocalisation() != null
                        ? new ReclamationPubliqueDTO.LocalisationLegere(
                        r.getLocalisation().getLatitude(),
                        r.getLocalisation().getLongitude(),
                        r.getLocalisation().getVille())
                        : null,
                // Visiteur anonyme : aucun nom d'auteur, même pas le prénom.
                (!anonyme && r.getCitoyen() != null) ? r.getCitoyen().getPrenom() : null,
                votesParReclamation.getOrDefault(r.getId(), 0L),
                idsDejaVotes.contains(r.getId())
        ));
    }

    /**
     * Bascule le vote du citoyen connecté sur une réclamation : vote s'il
     * n'avait pas encore voté, retire son vote sinon (comme un "j'aime"
     * qu'on peut annuler).
     *
     * @return le nouveau nombre total de votes, et si LE citoyen vient de voter (true) ou de retirer son vote (false)
     */
    @Transactional
    public VoteResultat basculerVote(String emailCitoyen, Long reclamationId) {
        Utilisateur citoyen = utilisateurRepository.findByEmail(emailCitoyen)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        Reclamation reclamation = reclamationRepository.findById(reclamationId)
                .orElseThrow(() -> new RuntimeException("Réclamation non trouvée"));

        if (reclamation.getCitoyen() != null
                && reclamation.getCitoyen().getId().equals(citoyen.getId())) {
            throw new AccesRefuseException("Vous ne pouvez pas voter pour votre propre réclamation.");
        }

        var voteExistant = voteRepository.findByReclamation_IdAndCitoyen_Id(reclamationId, citoyen.getId());

        boolean aVoteMaintenant;
        if (voteExistant.isPresent()) {
            voteRepository.delete(voteExistant.get());
            aVoteMaintenant = false;
        } else {
            Vote vote = new Vote();
            vote.setReclamation(reclamation);
            vote.setCitoyen(citoyen);
            vote.setValeur(1);
            vote.setDateVote(LocalDateTime.now());
            try {
                voteRepository.save(vote);
            } catch (DataIntegrityViolationException e) {
                // Cas rarissime : un autre clic quasi simultané du même citoyen
                // a déjà créé le vote entre notre vérification et cet insert.
                // On considère simplement que le vote existe déjà (pas d'erreur
                // renvoyée à l'utilisateur pour un simple double-clic rapide).
            }
            aVoteMaintenant = true;
        }

        long total = voteRepository.countByReclamation_Id(reclamationId);
        return new VoteResultat(total, aVoteMaintenant);
    }

    public record VoteResultat(long nombreVotes, boolean aVote) {}

    /**
     * Détail complet d'une réclamation, accessible à N'IMPORTE QUEL citoyen
     * (pas seulement son auteur) — contrairement à
     * ReclamationService.getReclamationDetaillee(), strictement réservé au
     * propriétaire. Utilisé depuis l'écran "Réclamations publiques".
     *
     * Contrairement au détail "privé", celui-ci n'inclut QUE le prénom du
     * citoyen auteur (jamais son email/téléphone), et l'historique de statut
     * ne contient aucune information sur qui a effectué le changement.
     */
    public Map<String, Object> getDetailPublique(Long reclamationId, String emailCitoyenConnecte) {
        Utilisateur moi = trouverUtilisateurOuNull(emailCitoyenConnecte);
        boolean anonyme = moi == null;
        Reclamation r = reclamationRepository.findById(reclamationId)
                .orElseThrow(() -> new RuntimeException("Réclamation non trouvée"));

        List<Media> medias = mediaRepository.findByReclamationId(reclamationId);
        List<HistoriqueStatut> historique =
                historiqueStatutRepository.findByReclamationIdOrderByDateChangementDesc(reclamationId);

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", r.getId());
        dto.put("reference", r.getReference());
        dto.put("titre", r.getTitre());
        dto.put("description", r.getDescription());
        dto.put("dateCreation", r.getDateCreation() != null ? r.getDateCreation().toString() : null);
        dto.put("statut", r.getStatut() != null ? r.getStatut().name() : null);
        dto.put("priorite", r.getPriorite() != null ? r.getPriorite().name() : null);
        dto.put("categorieNom", r.getCategorie() != null ? r.getCategorie().getNom() : null);
        if (r.getLocalisation() != null) {
            Map<String, Object> loc = new LinkedHashMap<>();
            loc.put("latitude", r.getLocalisation().getLatitude());
            loc.put("longitude", r.getLocalisation().getLongitude());
            loc.put("ville", r.getLocalisation().getVille());
            loc.put("quartier", r.getLocalisation().getQuartier());
            dto.put("localisation", loc);
        }
        // Uniquement le prénom : jamais l'email ni le téléphone de l'auteur.
        dto.put("citoyenPrenom", (!anonyme && r.getCitoyen() != null) ? r.getCitoyen().getPrenom() : null);

        dto.put("medias", medias.stream().map(m -> {
            Map<String, Object> md = new LinkedHashMap<>();
            md.put("url", m.getUrl());
            md.put("type", m.getType() != null ? m.getType().name() : null);
            return md;
        }).toList());

        // Historique "public" : dates + statuts + commentaire, jamais qui
        // a effectué le changement (pas d'info sur l'agent).
        dto.put("historique", historique.stream().map(h -> {
            Map<String, Object> hd = new LinkedHashMap<>();
            hd.put("ancienStatut", h.getAncienStatut() != null ? h.getAncienStatut().name() : null);
            hd.put("nouveauStatut", h.getNouveauStatut() != null ? h.getNouveauStatut().name() : null);
            hd.put("dateChangement", h.getDateChangement() != null ? h.getDateChangement().toString() : null);
            // Seul un motif saisi par une personne est montré : les textes automatiques nomment un agent.
            hd.put("commentaire", com.pfe.backend.service.MotifsHistorique.motifPublic(h.getCommentaire()));
            return hd;
        }).toList());

        dto.put("nombreVotes", voteRepository.countByReclamation_Id(reclamationId));
        dto.put("aVote", !anonyme
                && voteRepository.findByReclamation_IdAndCitoyen_Id(reclamationId, moi.getId()).isPresent());

        return dto;
    }

    /** null si aucun e-mail (visiteur sans compte) ; erreur si l'e-mail est inconnu. */
    private Utilisateur trouverUtilisateurOuNull(String email) {
        if (email == null || email.isBlank()) return null;
        return utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }
}