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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VoteService {

    private final VoteRepository voteRepository;
    private final ReclamationRepository reclamationRepository;
    private final UtilisateurRepository utilisateurRepository;

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

        Utilisateur moi = utilisateurRepository.findByEmail(emailCitoyenConnecte)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        Page<Reclamation> page = trierParVotes
                ? reclamationRepository.findPubliquesTrieesParVotes(zoneId, categorieId, statut, pageable)
                : reclamationRepository.findPubliques(zoneId, categorieId, statut, pageable);

        List<Long> ids = page.getContent().stream().map(Reclamation::getId).toList();

        // 2 requêtes groupées au lieu d'une par réclamation affichée.
        Map<Long, Long> votesParReclamation = voteRepository.countByReclamationIds(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        List<Long> idsDejaVotes = voteRepository.findReclamationIdsVotedBy(ids, moi.getId());

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
                r.getCitoyen() != null ? r.getCitoyen().getPrenom() : null,
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
}