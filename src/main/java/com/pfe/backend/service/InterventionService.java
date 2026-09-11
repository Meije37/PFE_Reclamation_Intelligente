package com.pfe.backend.service;

import com.pfe.backend.dto.InterventionCreateDTO;
import com.pfe.backend.dto.InterventionResponseDTO;
import com.pfe.backend.dto.InterventionStatutUpdateDTO;
import com.pfe.backend.entity.*;
import com.pfe.backend.entity.enums.StatutIntervention;
import com.pfe.backend.exception.AccesRefuseException;
import com.pfe.backend.exception.ResourceNotFoundException;
import com.pfe.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Documente le travail de terrain effectué sur une réclamation : qui est
 * intervenu, quand, avec quel résultat et à quel coût. Distinct de
 * HistoriqueStatut (qui trace juste les changements de statut de la
 * réclamation) — une Intervention porte le détail opérationnel.
 */
@Service
@RequiredArgsConstructor
public class InterventionService {

    private final InterventionRepository interventionRepository;
    private final ReclamationRepository reclamationRepository;
    private final com.pfe.backend.repository.ServiceRepository serviceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;

    @Transactional
    public InterventionResponseDTO planifier(Long reclamationId, String emailAppelant, InterventionCreateDTO dto) {
        Reclamation reclamation = reclamationRepository.findById(reclamationId)
                .orElseThrow(() -> new ResourceNotFoundException("Réclamation introuvable (id=" + reclamationId + ")."));

        // L'appelant (celui qui planifie) n'est plus forcément celui qui
        // exécute — on vérifie juste qu'il existe, mais ce n'est pas lui
        // qu'on assigne à l'intervention (voir agentCible plus bas).
        utilisateurRepository.findByEmail(emailAppelant)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + emailAppelant));

        com.pfe.backend.entity.Service service = serviceRepository.findById(dto.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service introuvable (id=" + dto.getServiceId() + ")."));

        Utilisateur agentCible = utilisateurRepository.findById(dto.getAgentId())
                .orElseThrow(() -> new ResourceNotFoundException("Agent introuvable (id=" + dto.getAgentId() + ")."));

        if (agentCible.getRole() != com.pfe.backend.entity.enums.Role.AGENT) {
            throw new IllegalArgumentException("L'utilisateur sélectionné n'est pas un agent.");
        }
        if (agentCible.getService() == null || !agentCible.getService().getId().equals(service.getId())) {
            throw new IllegalArgumentException("Cet agent n'appartient pas au service sélectionné.");
        }

        Intervention intervention = new Intervention();
        intervention.setReclamation(reclamation);
        intervention.setService(service);
        intervention.setAgent(agentCible);
        intervention.setStatutIntervention(StatutIntervention.PLANIFIEE);
        intervention.setDateDebut(dto.getDateDebut() != null
                ? LocalDateTime.parse(dto.getDateDebut())
                : LocalDateTime.now());

        Intervention enregistree = interventionRepository.save(intervention);

        // L'agent cible est notifié qu'une intervention lui a été confiée —
        // sans ça, il n'aurait aucun moyen de savoir qu'il doit s'en occuper.
        notificationService.creer(
                agentCible,
                "Nouvelle intervention à planifier",
                "Une intervention vous a été confiée sur la réclamation \"" + reclamation.getTitre()
                        + "\" (réf. " + reclamation.getReference() + ").",
                reclamation.getId()
        );

        return versDTO(enregistree);
    }

    @Transactional
    public InterventionResponseDTO changerStatut(Long interventionId, String emailAgent, InterventionStatutUpdateDTO dto) {
        Intervention intervention = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable (id=" + interventionId + ")."));

        Utilisateur agent = utilisateurRepository.findByEmail(emailAgent)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + emailAgent));

        boolean estProprietaire = intervention.getAgent() != null
                && intervention.getAgent().getId().equals(agent.getId());
        boolean estAdmin = agent.getRole() == com.pfe.backend.entity.enums.Role.ADMIN;
        if (!estProprietaire && !estAdmin) {
            throw new AccesRefuseException("Vous n'êtes pas l'agent assigné à cette intervention.");
        }

        StatutIntervention nouveauStatut;
        try {
            nouveauStatut = StatutIntervention.valueOf(dto.getStatut());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Statut d'intervention invalide : " + dto.getStatut());
        }

        intervention.setStatutIntervention(nouveauStatut);

        if (nouveauStatut == StatutIntervention.TERMINEE) {
            intervention.setDateFin(LocalDateTime.now());
            intervention.setResultat(dto.getResultat());
            intervention.setCoutEstime(dto.getCoutEstime());
            notifierFinIntervention(intervention);
        }

        Intervention sauvegardee = interventionRepository.save(intervention);
        return versDTO(sauvegardee);
    }

    @Transactional(readOnly = true)
    public List<InterventionResponseDTO> listerPourReclamation(Long reclamationId) {
        if (!reclamationRepository.existsById(reclamationId)) {
            throw new ResourceNotFoundException("Réclamation introuvable (id=" + reclamationId + ").");
        }
        return interventionRepository.findByReclamationIdOrderByDateDebutDesc(reclamationId)
                .stream().map(this::versDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InterventionResponseDTO> listerPourAgent(String emailAgent) {
        Utilisateur agent = utilisateurRepository.findByEmail(emailAgent)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + emailAgent));
        return interventionRepository.findByAgentIdOrderByDateDebutDesc(agent.getId())
                .stream().map(this::versDTO).collect(Collectors.toList());
    }

    private void notifierFinIntervention(Intervention intervention) {
        Reclamation reclamation = intervention.getReclamation();
        if (reclamation != null && reclamation.getCitoyen() != null) {
            notificationService.creer(
                    reclamation.getCitoyen(),
                    "Intervention terminée",
                    "L'intervention sur votre réclamation \"" + reclamation.getTitre()
                            + "\" (réf. " + reclamation.getReference() + ") est terminée.",
                    reclamation.getId()
            );
        }
    }

    private InterventionResponseDTO versDTO(Intervention i) {
        return new InterventionResponseDTO(
                i.getIdIntervention(),
                i.getDateDebut(),
                i.getDateFin(),
                i.getStatutIntervention() != null ? i.getStatutIntervention().name() : null,
                i.getResultat(),
                i.getCoutEstime(),
                i.getService() != null ? i.getService().getNom() : null,
                i.getAgent() != null ? i.getAgent().getPrenom() + " " + i.getAgent().getNom() : null
        );
    }
}