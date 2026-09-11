package com.pfe.backend.service;

import com.pfe.backend.dto.CommentaireResponseDTO;
import com.pfe.backend.entity.Affectation;
import com.pfe.backend.entity.Commentaire;
import com.pfe.backend.entity.Reclamation;
import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.entity.enums.Role;
import com.pfe.backend.entity.enums.VisibiliteCommentaire;
import com.pfe.backend.repository.AffectationRepository;
import com.pfe.backend.repository.CommentaireRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentaireService {

    private final CommentaireRepository commentaireRepository;
    private final AffectationRepository affectationRepository;
    private final NotificationService notificationService;

    /**
     * Vérifie que l'utilisateur a le droit d'accéder (lire/commenter) à
     * cette réclamation. Lève une exception sinon.
     */
    public void verifierAcces(Reclamation reclamation, Utilisateur utilisateur) {
        if (utilisateur.getRole() == Role.ADMIN) return;

        if (utilisateur.getRole() == Role.CITOYEN) {
            if (!reclamation.getCitoyen().getId().equals(utilisateur.getId())) {
                throw new RuntimeException("Accès non autorisé à cette réclamation.");
            }
            return;
        }

        if (utilisateur.getRole() == Role.AGENT) {
            boolean assigne = !affectationRepository
                    .findByAgentIdAndReclamationId(utilisateur.getId(), reclamation.getId())
                    .isEmpty();
            if (!assigne) {
                throw new RuntimeException("Cette réclamation ne vous est pas assignée.");
            }
            return;
        }

        throw new RuntimeException("Accès non autorisé.");
    }

    public List<CommentaireResponseDTO> lister(Reclamation reclamation, Utilisateur utilisateur) {
        verifierAcces(reclamation, utilisateur);

        List<Commentaire> commentaires =
                commentaireRepository.findByReclamationOrderByDateCommentaireAsc(reclamation);

        // Un citoyen ne voit jamais les commentaires INTERNE, quelle que
        // soit la façon dont la requête est faite — filtrage côté serveur.
        if (utilisateur.getRole() == Role.CITOYEN) {
            commentaires = commentaires.stream()
                    .filter(c -> c.getVisibilite() == VisibiliteCommentaire.PUBLIC)
                    .collect(Collectors.toList());
        }

        return commentaires.stream().map(this::versDTO).collect(Collectors.toList());
    }

    public CommentaireResponseDTO ajouter(Reclamation reclamation, Utilisateur auteur,
                                          String contenu, String visibiliteDemandee) {
        verifierAcces(reclamation, auteur);

        if (contenu == null || contenu.trim().isEmpty()) {
            throw new RuntimeException("Le commentaire ne peut pas être vide.");
        }

        // Sécurité : un citoyen ne peut jamais poster un commentaire INTERNE,
        // quoi que le client envoie — on force PUBLIC côté serveur.
        VisibiliteCommentaire visibilite;
        if (auteur.getRole() == Role.CITOYEN) {
            visibilite = VisibiliteCommentaire.PUBLIC;
        } else {
            visibilite = "INTERNE".equalsIgnoreCase(visibiliteDemandee)
                    ? VisibiliteCommentaire.INTERNE
                    : VisibiliteCommentaire.PUBLIC;
        }

        Commentaire commentaire = new Commentaire();
        commentaire.setReclamation(reclamation);
        commentaire.setAuteur(auteur);
        commentaire.setContenu(contenu.trim());
        commentaire.setVisibilite(visibilite);
        Commentaire sauvegarde = commentaireRepository.save(commentaire);

        notifierApresCommentaire(reclamation, auteur, visibilite);

        return versDTO(sauvegarde);
    }

    /**
     * Notifie la "partie adverse" quand un commentaire est ajouté :
     * - le citoyen commente → notifie l'agent assigné (si commentaire visible par lui, toujours le cas)
     * - un agent/admin commente en PUBLIC → notifie le citoyen (jamais si INTERNE, le citoyen ne doit pas le savoir)
     */
    private void notifierApresCommentaire(Reclamation reclamation, Utilisateur auteur,
                                          VisibiliteCommentaire visibilite) {
        String titreRef = reclamation.getTitre() + " (réf. " + reclamation.getReference() + ")";

        if (auteur.getRole() == Role.CITOYEN) {
            Optional<Utilisateur> agent = agentAssigne(reclamation);
            agent.ifPresent(a -> notificationService.creer(
                    a,
                    "Nouveau commentaire",
                    "Le citoyen a commenté la réclamation \"" + titreRef + "\".",
                    reclamation.getId()
            ));
        } else if (visibilite == VisibiliteCommentaire.PUBLIC && reclamation.getCitoyen() != null) {
            notificationService.creer(
                    reclamation.getCitoyen(),
                    "Nouveau commentaire",
                    "Un agent a répondu sur votre réclamation \"" + titreRef + "\".",
                    reclamation.getId()
            );
        }
    }

    private Optional<Utilisateur> agentAssigne(Reclamation reclamation) {
        List<Affectation> affectations = affectationRepository
                .findByReclamationIdOrderByDateAffectationDesc(reclamation.getId());
        return affectations.isEmpty() ? Optional.empty() : Optional.of(affectations.get(0).getAgent());
    }

    private CommentaireResponseDTO versDTO(Commentaire c) {
        Utilisateur auteur = c.getAuteur();
        return new CommentaireResponseDTO(
                c.getIdCommentaire(),
                c.getContenu(),
                c.getVisibilite() != null ? c.getVisibilite().name() : null,
                c.getDateCommentaire(),
                auteur != null ? (auteur.getPrenom() + " " + auteur.getNom()) : "Utilisateur",
                auteur != null && auteur.getRole() != null ? auteur.getRole().name() : null,
                auteur != null ? auteur.getId() : null
        );
    }
}