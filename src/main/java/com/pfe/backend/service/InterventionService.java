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
import com.pfe.backend.dto.InterventionTransfertDTO;

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
    private final AffectationRepository affectationRepository;
    private final NotificationService notificationService;

    @Transactional
    public InterventionResponseDTO planifier(Long reclamationId, String emailAppelant, InterventionCreateDTO dto) {
        Reclamation reclamation = reclamationRepository.findById(reclamationId)
                .orElseThrow(() -> new ResourceNotFoundException("Réclamation introuvable (id=" + reclamationId + ")."));

        // L'appelant (celui qui planifie) n'est plus forcément celui qui
        // exécute (voir agentCible plus bas) : il doit en revanche avoir accès
        // au dossier (agent affecté, agent intervenant, ou admin).
        Utilisateur appelant = utilisateurRepository.findByEmail(emailAppelant)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + emailAppelant));
        verifierPeutPlanifier(reclamation, appelant);

        // On ne planifie pas de travail sur un dossier déjà clos ou refusé.
        com.pfe.backend.entity.enums.StatutReclamation statutReclamation = reclamation.getStatut();
        if (statutReclamation == com.pfe.backend.entity.enums.StatutReclamation.RESOLUE
                || statutReclamation == com.pfe.backend.entity.enums.StatutReclamation.ANNULEE
                || statutReclamation == com.pfe.backend.entity.enums.StatutReclamation.REJETEE
                || statutReclamation == com.pfe.backend.entity.enums.StatutReclamation.FERMEE) {
            throw new IllegalArgumentException(
                    "Impossible de planifier une intervention sur une réclamation "
                            + libelleStatutReclamation(statutReclamation) + ".");
        }

        com.pfe.backend.entity.Service service = serviceRepository.findById(dto.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service introuvable (id=" + dto.getServiceId() + ")."));

        if (Boolean.FALSE.equals(service.getActif())) {
            throw new IllegalArgumentException("Ce service n'est plus actif.");
        }

        // Pas d'agent précisé : le système choisit (charge de travail, continuité sur le dossier).
        boolean automatique = dto.getAgentId() == null;
        Utilisateur agentCible;
        if (automatique) {
            agentCible = choisirAgentAutomatiquement(reclamation, service, null);
        } else {
            agentCible = utilisateurRepository.findById(dto.getAgentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agent introuvable (id=" + dto.getAgentId() + ")."));
            verifierAgentDuService(agentCible, service);
        }

        Intervention intervention = new Intervention();
        intervention.setReclamation(reclamation);
        intervention.setService(service);
        intervention.setAgent(agentCible);
        intervention.setStatutIntervention(StatutIntervention.PLANIFIEE);
        intervention.setPlanifiePar(appelant);
        intervention.setAffectationAutomatique(automatique);
        intervention.setDateDebut(parserDateDebut(dto.getDateDebut()));

        Intervention enregistree = interventionRepository.save(intervention);

        // L'agent cible est notifié qu'une intervention lui a été confiée —
        // sauf s'il se l'est planifiée lui-même (inutile de se notifier soi-même).
        if (!agentCible.getId().equals(appelant.getId())) {
            notificationService.creer(
                    agentCible,
                    "Nouvelle intervention à effectuer",
                    "Une intervention vous a été confiée sur la réclamation \"" + reclamation.getTitre()
                            + "\" (réf. " + reclamation.getReference() + ")."
                            + (automatique ? " Attribution automatique." : ""),
                    reclamation.getId()
            );
        }

        return versDTO(enregistree, appelant);
    }

    @Transactional
    public InterventionResponseDTO changerStatut(Long interventionId, String emailAgent, InterventionStatutUpdateDTO dto) {
        Intervention intervention = interventionRepository.findByIdForUpdate(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable (id=" + interventionId + ")."));

        Utilisateur agent = utilisateurRepository.findByEmail(emailAgent)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + emailAgent));

        boolean estProprietaire = intervention.getAgent() != null
                && intervention.getAgent().getId().equals(agent.getId());
        boolean estAdmin = agent.getRole() == com.pfe.backend.entity.enums.Role.ADMIN;
        boolean estPlanificateur = intervention.getPlanifiePar() != null
                && intervention.getPlanifiePar().getId().equals(agent.getId());
        if (!estProprietaire && !estAdmin && !estPlanificateur) {
            throw new AccesRefuseException("Vous n'êtes pas l'agent assigné à cette intervention.");
        }

        StatutIntervention nouveauStatut;
        try {
            nouveauStatut = StatutIntervention.valueOf(dto.getStatut());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Statut d'intervention invalide : " + dto.getStatut());
        }

        StatutIntervention statutActuel = intervention.getStatutIntervention() != null
                ? intervention.getStatutIntervention()
                : StatutIntervention.PLANIFIEE;
        // Le planificateur (qui n'est ni l'exécutant ni admin) ne peut qu'annuler
        // une intervention qui n'a pas encore démarré.
        if (!estProprietaire && !estAdmin
                && !(nouveauStatut == StatutIntervention.ANNULEE && statutActuel == StatutIntervention.PLANIFIEE)) {
            throw new AccesRefuseException(
                    "Vous pouvez seulement annuler une intervention que vous avez planifiée et qui n'a pas démarré.");
        }
        if (!transitionAutorisee(statutActuel, nouveauStatut)) {
            throw new IllegalArgumentException(
                    "Transition impossible : une intervention " + libelle(statutActuel)
                            + " ne peut pas passer à " + libelle(nouveauStatut) + ".");
        }

        // Clôture : le résultat est obligatoire (trace de ce qui a été fait) et le coût
        // doit être un nombre positif ou nul. On valide AVANT toute modification.
        String resultat = null;
        if (nouveauStatut == StatutIntervention.TERMINEE) {
            resultat = dto.getResultat() != null ? dto.getResultat().trim() : "";
            if (resultat.isEmpty()) {
                throw new IllegalArgumentException("Le résultat de l'intervention est obligatoire pour la terminer.");
            }
            if (resultat.length() > 1000) {
                throw new IllegalArgumentException("Le résultat ne doit pas dépasser 1000 caractères.");
            }
            Double cout = dto.getCoutEstime();
            if (cout != null && (cout.isNaN() || cout.isInfinite() || cout < 0)) {
                throw new IllegalArgumentException("Le coût estimé doit être un nombre positif ou nul.");
            }
            // Cohérence des dates : une intervention ne peut pas finir avant sa date prévue.
            LocalDateTime prevue = intervention.getDateDebut();
            if (prevue != null && prevue.isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException(
                        "Impossible de terminer une intervention avant sa date prévue ("
                                + prevue.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + ").");
            }
        }

        intervention.setStatutIntervention(nouveauStatut);

        if (nouveauStatut == StatutIntervention.EN_COURS) {
            intervention.setDateDemarrage(LocalDateTime.now());
        }
        if (nouveauStatut == StatutIntervention.TERMINEE) {
            intervention.setDateFin(LocalDateTime.now());
            intervention.setResultat(resultat);
            intervention.setCoutEstime(dto.getCoutEstime());
        }

        Intervention sauvegardee = interventionRepository.save(intervention);

        // Les notifications partent APRÈS l'enregistrement : on n'annonce jamais (au citoyen,
        // au planificateur, au responsable) un changement qui n'aurait pas été persisté.
        if (nouveauStatut == StatutIntervention.TERMINEE) {
            notifierFinIntervention(sauvegardee);
            notifierTerminaison(sauvegardee, agent);
        } else if (nouveauStatut == StatutIntervention.ANNULEE) {
            notifierAnnulation(sauvegardee, agent);
        }
        return versDTO(sauvegardee, agent);
    }

    @Transactional(readOnly = true)
    public List<InterventionResponseDTO> listerPourReclamation(Long reclamationId, String emailAppelant) {
        Reclamation reclamation = reclamationRepository.findById(reclamationId)
                .orElseThrow(() -> new ResourceNotFoundException("Réclamation introuvable (id=" + reclamationId + ")."));
        Utilisateur appelant = utilisateurRepository.findByEmail(emailAppelant)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + emailAppelant));
        verifierAcces(reclamation, appelant);

        return interventionRepository.findByReclamationIdOrderByDateDebutDesc(reclamationId)
                .stream()
                .map(i -> versDTO(i, appelant))
                .collect(Collectors.toList());
    }

    /** Version publique pour les contrôleurs (ex : pré-remplissage du formulaire). */
    @Transactional(readOnly = true)
    public void verifierAccesReclamation(Long reclamationId, String emailAppelant) {
        Reclamation reclamation = reclamationRepository.findById(reclamationId)
                .orElseThrow(() -> new ResourceNotFoundException("Réclamation introuvable (id=" + reclamationId + ")."));
        Utilisateur appelant = utilisateurRepository.findByEmail(emailAppelant)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + emailAppelant));
        verifierAcces(reclamation, appelant);
    }

    /**
     * Même règle d'accès que le détail de la réclamation côté agent :
     * admin, agent affecté, ou agent à qui une intervention a été confiée.
     */
    private void verifierAcces(Reclamation reclamation, Utilisateur appelant) {
        if (estAdmin(appelant)) return;

        boolean affecte = !affectationRepository
                .findByAgentIdAndReclamationId(appelant.getId(), reclamation.getId()).isEmpty();
        boolean intervenant = interventionRepository
                .existsByReclamationIdAndAgentId(reclamation.getId(), appelant.getId());

        if (!affecte && !intervenant) {
            throw new AccesRefuseException("Vous n'avez pas accès aux interventions de cette réclamation.");
        }
    }

    private boolean estAdmin(Utilisateur u) {
        return u.getRole() == com.pfe.backend.entity.enums.Role.ADMIN;
    }

    @Transactional(readOnly = true)
    public List<InterventionResponseDTO> listerPourAgent(String emailAgent) {
        Utilisateur agent = utilisateurRepository.findByEmail(emailAgent)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + emailAgent));
        return interventionRepository.findByAgentIdOrderByDateDebutDesc(agent.getId())
                .stream().map(i -> versDTO(i, agent)).collect(Collectors.toList());
    }

    /**
     * Cycle de vie autorisé d'une intervention :
     *   PLANIFIEE -> EN_COURS | ANNULEE
     *   EN_COURS  -> TERMINEE | ANNULEE
     *   TERMINEE et ANNULEE sont des états finaux.
     */
    private boolean transitionAutorisee(StatutIntervention de, StatutIntervention vers) {
        return switch (de) {
            case PLANIFIEE -> vers == StatutIntervention.EN_COURS || vers == StatutIntervention.ANNULEE;
            case EN_COURS  -> vers == StatutIntervention.TERMINEE || vers == StatutIntervention.ANNULEE;
            case TERMINEE, ANNULEE -> false;
        };
    }

    private String libelle(StatutIntervention s) {
        return switch (s) {
            case PLANIFIEE -> "planifiée";
            case EN_COURS  -> "en cours";
            case TERMINEE  -> "terminée";
            case ANNULEE   -> "annulée";
        };
    }

    /**
     * Transfère une intervention PLANIFIEE (pas encore démarrée) à un autre agent du même service,
     * choisi par l'utilisateur ou, sans agentId, automatiquement. Utile quand l'exécutant est
     * absent ou indisponible : sans cela, l'intervention resterait bloquée.
     */
    @Transactional
    public InterventionResponseDTO transferer(Long interventionId, String emailAppelant, InterventionTransfertDTO dto) {
        Intervention intervention = interventionRepository.findByIdForUpdate(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable (id=" + interventionId + ")."));
        Utilisateur appelant = utilisateurRepository.findByEmail(emailAppelant)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + emailAppelant));

        Utilisateur ancien = intervention.getAgent();
        boolean estProprietaire = ancien != null && ancien.getId().equals(appelant.getId());
        boolean estPlanificateur = intervention.getPlanifiePar() != null
                && intervention.getPlanifiePar().getId().equals(appelant.getId());
        if (!estAdmin(appelant) && !estProprietaire && !estPlanificateur) {
            throw new AccesRefuseException("Vous ne pouvez pas transférer cette intervention.");
        }
        if (intervention.getStatutIntervention() != StatutIntervention.PLANIFIEE) {
            throw new IllegalArgumentException(
                    "Seule une intervention planifiée (pas encore démarrée) peut être transférée.");
        }

        com.pfe.backend.entity.Service service = intervention.getService();
        if (service == null) {
            throw new IllegalArgumentException("Cette intervention n'est rattachée à aucun service.");
        }

        Long idAncien = ancien != null ? ancien.getId() : null;
        boolean automatique = dto == null || dto.getAgentId() == null;
        Utilisateur nouveau;
        if (automatique) {
            nouveau = choisirAgentAutomatiquement(intervention.getReclamation(), service, idAncien);
        } else {
            if (dto.getAgentId().equals(idAncien)) {
                throw new IllegalArgumentException("Cette intervention est déjà confiée à cet agent.");
            }
            nouveau = utilisateurRepository.findById(dto.getAgentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agent introuvable (id=" + dto.getAgentId() + ")."));
            verifierAgentDuService(nouveau, service);
        }

        intervention.setAgent(nouveau);
        intervention.setAffectationAutomatique(automatique);
        Intervention sauvegardee = interventionRepository.save(intervention);

        notifierTransfert(sauvegardee, ancien, appelant);
        return versDTO(sauvegardee, appelant);
    }

    /** Écart de charge toléré pour privilégier la continuité (agent déjà intervenu sur le dossier). */
    private static final int ECART_CHARGE_TOLERE = 2;

    /**
     * Choisit l'agent actif du service le plus adapté :
     *  1. on ne garde que les agents dont la charge (interventions planifiées + en cours) reste
     *     proche (+2) de la plus faible du service : personne n'est surchargé ;
     *  2. parmi eux, on préfère celui qui a déjà travaillé sur CE dossier (continuité) ;
     *  3. puis le moins chargé, puis celui qui a le moins d'interventions au total, puis l'id (stable).
     */
    private Utilisateur choisirAgentAutomatiquement(Reclamation reclamation,
                                                    com.pfe.backend.entity.Service service,
                                                    Long exclureAgentId) {
        List<Utilisateur> candidats = utilisateurRepository
                .findByServiceIdAndRoleAndActifTrue(service.getId(), com.pfe.backend.entity.enums.Role.AGENT)
                .stream()
                .filter(a -> exclureAgentId == null || !a.getId().equals(exclureAgentId))
                .collect(Collectors.toList());

        if (candidats.isEmpty()) {
            throw new IllegalArgumentException(
                    (exclureAgentId == null ? "Aucun agent actif disponible" : "Aucun autre agent actif disponible")
                            + " dans le service « " + service.getNom() + " ».");
        }

        java.util.Set<Long> dejaIntervenus = interventionRepository
                .findByReclamationIdOrderByDateDebutDesc(reclamation.getId())
                .stream()
                .filter(i -> i.getAgent() != null)
                .map(i -> i.getAgent().getId())
                .collect(Collectors.toSet());

        java.util.Map<Long, Long> chargeActive = new java.util.HashMap<>();
        for (Utilisateur a : candidats) {
            chargeActive.put(a.getId(), interventionRepository.countByAgentIdAndStatutInterventionIn(
                    a.getId(), List.of(StatutIntervention.PLANIFIEE, StatutIntervention.EN_COURS)));
        }
        long chargeMin = java.util.Collections.min(chargeActive.values());

        return candidats.stream()
                .filter(a -> chargeActive.get(a.getId()) <= chargeMin + ECART_CHARGE_TOLERE)
                .min(java.util.Comparator
                        .comparing((Utilisateur a) -> !dejaIntervenus.contains(a.getId()))
                        .thenComparingLong(a -> chargeActive.get(a.getId()))
                        .thenComparingLong(a -> interventionRepository.countByAgentId(a.getId()))
                        .thenComparing(Utilisateur::getId))
                .orElse(candidats.get(0));
    }

    private void verifierAgentDuService(Utilisateur agent, com.pfe.backend.entity.Service service) {
        if (agent.getRole() != com.pfe.backend.entity.enums.Role.AGENT) {
            throw new IllegalArgumentException("L'utilisateur sélectionné n'est pas un agent.");
        }
        if (agent.getService() == null || !agent.getService().getId().equals(service.getId())) {
            throw new IllegalArgumentException("Cet agent n'appartient pas au service sélectionné.");
        }
        if (Boolean.FALSE.equals(agent.getActif())) {
            throw new IllegalArgumentException("Cet agent n'est plus actif.");
        }
    }

    /** Nouvel exécutant, ancien exécutant et planificateur sont prévenus (sans doublon, sans l'auteur). */
    private void notifierTransfert(Intervention intervention, Utilisateur ancien, Utilisateur auteur) {
        Reclamation reclamation = intervention.getReclamation();
        Utilisateur nouveau = intervention.getAgent();
        String dossier = "\"" + reclamation.getTitre() + "\" (réf. " + reclamation.getReference() + ")";
        String nomAuteur = auteur.getPrenom() + " " + auteur.getNom();
        String nomNouveau = nouveau.getPrenom() + " " + nouveau.getNom();

        if (!nouveau.getId().equals(auteur.getId())) {
            notificationService.creer(
                    nouveau,
                    "Intervention transférée",
                    "Une intervention sur la réclamation " + dossier + " vous a été transférée par " + nomAuteur + ".",
                    reclamation.getId());
        }

        java.util.Set<Long> dejaPrevenus = new java.util.HashSet<>();
        dejaPrevenus.add(auteur.getId());
        dejaPrevenus.add(nouveau.getId());
        for (Utilisateur u : java.util.Arrays.asList(ancien, intervention.getPlanifiePar())) {
            if (u == null || !dejaPrevenus.add(u.getId())) continue;
            notificationService.creer(
                    u,
                    "Intervention transférée",
                    "L'intervention sur la réclamation " + dossier + " a été transférée à "
                            + nomNouveau + " par " + nomAuteur + ".",
                    reclamation.getId());
        }
    }

    /** Agent de l'affectation la plus récente = responsable actuel du dossier (null si aucun). */
    private Utilisateur responsableActuel(Long reclamationId) {
        return affectationRepository.findByReclamationIdOrderByDateAffectationDesc(reclamationId)
                .stream()
                .findFirst()
                .map(a -> a.getAgent())
                .orElse(null);
    }

    /**
     * Planifier est réservé à l'admin, au responsable ACTUEL du dossier et aux agents
     * qui y ont déjà une intervention (un ancien responsable ne peut plus planifier).
     */
    private void verifierPeutPlanifier(Reclamation reclamation, Utilisateur appelant) {
        if (estAdmin(appelant)) return;

        Utilisateur responsable = responsableActuel(reclamation.getId());
        boolean estResponsable = responsable != null && responsable.getId().equals(appelant.getId());
        boolean intervenant = interventionRepository
                .existsByReclamationIdAndAgentId(reclamation.getId(), appelant.getId());

        if (!estResponsable && !intervenant) {
            throw new AccesRefuseException(
                    "Seul l'agent responsable du dossier, un agent intervenant ou un administrateur "
                            + "peut planifier une intervention.");
        }
    }

    private LocalDateTime parserDateDebut(String valeur) {
        if (valeur == null || valeur.isBlank()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(valeur);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Date de début invalide (format attendu : 2026-08-15T09:00:00).");
        }
    }

    private String libelleStatutReclamation(com.pfe.backend.entity.enums.StatutReclamation s) {
        return switch (s) {
            case RESOLUE -> "résolue";
            case ANNULEE -> "annulée";
            case REJETEE -> "rejetée";
            case FERMEE  -> "fermée";
            default      -> s.name().toLowerCase();
        };
    }

    /**
     * Personnes à prévenir d'un changement sur une intervention : celle qui l'a planifiée
     * et celle qui l'exécute, sans doublon et sans l'auteur de l'action lui-même.
     */
    private List<Utilisateur> personnesConcernees(Intervention intervention, Utilisateur auteur) {
        List<Utilisateur> resultat = new java.util.ArrayList<>();
        for (Utilisateur u : java.util.Arrays.asList(intervention.getPlanifiePar(), intervention.getAgent())) {
            if (u == null || u.getId().equals(auteur.getId())) continue;
            if (resultat.stream().noneMatch(x -> x.getId().equals(u.getId()))) {
                resultat.add(u);
            }
        }
        return resultat;
    }

    /**
     * Intervention terminée :
     *  - le planificateur (et l'exécutant si c'est quelqu'un d'autre qui clôture, ex : un admin)
     *    sont prévenus, même s'il reste d'autres interventions sur le dossier ;
     *  - si c'était la DERNIÈRE intervention active, le responsable du dossier reçoit à la place
     *    un message plus fort : il peut maintenant marquer la réclamation comme résolue.
     */
    private void notifierTerminaison(Intervention intervention, Utilisateur auteur) {
        Reclamation reclamation = intervention.getReclamation();

        boolean resteActive = interventionRepository
                .findByReclamationIdOrderByDateDebutDesc(reclamation.getId())
                .stream()
                .anyMatch(i -> i.getStatutIntervention() == StatutIntervention.PLANIFIEE
                        || i.getStatutIntervention() == StatutIntervention.EN_COURS);

        Utilisateur responsable = null;
        if (!resteActive
                && reclamation.getStatut() == com.pfe.backend.entity.enums.StatutReclamation.EN_COURS) {
            responsable = responsableActuel(reclamation.getId());
            // Si c'est lui qui clôture, il a déjà la fenêtre « marquer comme résolue ? ».
            if (responsable != null && responsable.getId().equals(auteur.getId())) {
                responsable = null;
            }
        }

        for (Utilisateur u : personnesConcernees(intervention, auteur)) {
            if (responsable != null && u.getId().equals(responsable.getId())) continue; // notifié ci-dessous
            notificationService.creer(
                    u,
                    "Intervention terminée",
                    "L'intervention sur la réclamation \"" + reclamation.getTitre()
                            + "\" (réf. " + reclamation.getReference() + ") a été terminée par "
                            + auteur.getPrenom() + " " + auteur.getNom() + ".",
                    reclamation.getId()
            );
        }

        if (responsable != null) {
            notificationService.creer(
                    responsable,
                    "Interventions terminées",
                    "Toutes les interventions sur la réclamation \"" + reclamation.getTitre()
                            + "\" (réf. " + reclamation.getReference()
                            + ") sont terminées. Vous pouvez la marquer comme résolue.",
                    reclamation.getId()
            );
        }
    }

    /** Intervention annulée : le planificateur et l'exécutant (sauf l'auteur) en sont informés. */
    private void notifierAnnulation(Intervention intervention, Utilisateur auteur) {
        Reclamation reclamation = intervention.getReclamation();
        for (Utilisateur u : personnesConcernees(intervention, auteur)) {
            notificationService.creer(
                    u,
                    "Intervention annulée",
                    "L'intervention sur la réclamation \"" + reclamation.getTitre()
                            + "\" (réf. " + reclamation.getReference() + ") a été annulée par "
                            + auteur.getPrenom() + " " + auteur.getNom() + ".",
                    reclamation.getId()
            );
        }
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

    private InterventionResponseDTO versDTO(Intervention i, Utilisateur appelant) {
        boolean proprietaire = i.getAgent() != null && i.getAgent().getId().equals(appelant.getId());
        boolean planificateur = i.getPlanifiePar() != null && i.getPlanifiePar().getId().equals(appelant.getId());

        // Contrôle complet (démarrer / terminer / annuler) : exécutant ou admin.
        boolean peutModifier = estAdmin(appelant) || proprietaire;
        // Le planificateur peut en plus annuler tant que l'intervention n'a pas démarré.
        boolean peutAnnuler = peutModifier
                || (planificateur && i.getStatutIntervention() == StatutIntervention.PLANIFIEE);
        // Transfert : tant que le travail n'a pas démarré, par l'exécutant, le planificateur ou un admin.
        boolean peutTransferer = i.getStatutIntervention() == StatutIntervention.PLANIFIEE
                && (peutModifier || planificateur);

        return new InterventionResponseDTO(
                i.getIdIntervention(),
                i.getDateDebut(),
                i.getDateFin(),
                i.getStatutIntervention() != null ? i.getStatutIntervention().name() : null,
                i.getResultat(),
                i.getCoutEstime(),
                i.getService() != null ? i.getService().getNom() : null,
                i.getAgent() != null ? i.getAgent().getPrenom() + " " + i.getAgent().getNom() : null,
                peutModifier,
                peutAnnuler,
                i.getPlanifiePar() != null
                        ? i.getPlanifiePar().getPrenom() + " " + i.getPlanifiePar().getNom() : null,
                i.getAgent() != null ? i.getAgent().getId() : null,
                i.getService() != null ? i.getService().getId() : null,
                peutTransferer,
                Boolean.TRUE.equals(i.getAffectationAutomatique()),
                i.getDateDemarrage()
        );
    }
}