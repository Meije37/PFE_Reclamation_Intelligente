package com.pfe.backend.service;

import com.pfe.backend.dto.ChangerStatutRequestDTO;
import com.pfe.backend.dto.ReclamationRequestDTO;
import com.pfe.backend.entity.*;
import com.pfe.backend.entity.enums.ModeAffectation;
import com.pfe.backend.entity.enums.Priorite;
import com.pfe.backend.entity.enums.Role;
import com.pfe.backend.entity.enums.StatutReclamation;
import com.pfe.backend.entity.enums.TypeAnalyseIA;
import com.pfe.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReclamationService {

    private final ReclamationRepository reclamationRepository;
    private final CategorieReclamationRepository categorieRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final LocalisationRepository localisationRepository;
    private final ServiceIAClient serviceIA;
    private final DoublonIAService doublonIAService;
    private final AffectationRepository affectationRepository;
    private final HistoriqueStatutRepository historiqueRepository;
    private final ServiceRepository serviceRepository;
    private final NotificationService notificationService;
    private final AnalyseIARepository analyseIARepository;

    private static final Logger log = LoggerFactory.getLogger(ReclamationService.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    // Recherche des doublons : mêmes valeurs que models/doublons.json côté IA (rayon et fenêtre).
    private static final double RAYON_RECHERCHE_M = 300;
    private static final int FENETRE_JOURS = 30;
    private static final int MAX_CANDIDATS = 100;
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

        // ── IA (microservice FastAPI) : municipal ? priorité ? type d'incident ? ──────────
        // Titre + description : le titre résume souvent le problème mieux que la description seule.
        String texte = (dto.getTitre() == null ? "" : dto.getTitre() + ". ") + dto.getDescription();
        Optional<ServiceIAClient.ResultatAnalyse> analyseIA = serviceIA.analyser(texte);

        if (analyseIA.isPresent()) {
            ServiceIAClient.ResultatAnalyse a = analyseIA.get();
            rec.setScoreUrgence(a.scoreUrgence());
            rec.setPriorite(prioriteDe(a.priorite(), categorie.getPrioriteParDefaut()));
            // Proposition de l'IA conservée telle quelle : un humain pourra la confirmer ou la corriger.
            rec.setPrioriteIA(rec.getPriorite());
            rec.setAlerteCritiqueIA(a.alerteCritique());
            rec.setIncidentIA(a.incident());
            rec.setProbabiliteMunicipal(a.probabiliteMunicipal());
            // Hors périmètre selon l'IA, OU confiance < 90 % : la décision revient à un humain.
            rec.setPerimetreAVerifier(!a.estMunicipal() || !a.decisionAutomatique());
            rec.setConfiancePrioriteIA(a.confiancePriorite());
            // Le citoyen s'est-il trompé de catégorie ? (ex. coupure d'eau déposée en « Électricité »)
            CategorieReclamation suggeree = categorieSuggeree(categorie, a);
            rec.setFamilleSuggereeIA(a.familleSuggeree());
            rec.setCategorieSuggereeIA(suggeree);
            rec.setCategorieAVerifier(suggeree != null);
        } else {
            // Repli si le service IA est arrêté : on ne bloque jamais le dépôt d'une réclamation.
            rec.setScoreUrgence(0.5);
            rec.setPriorite(determinerPriorite(0.5, categorie.getPrioriteParDefaut()));
        }

        // ── IA : doublons (même problème déjà signalé tout près, récemment) ─────────────
        List<Reclamation> candidats = chercherCandidatsDoublon(dto.getLatitude(), dto.getLongitude());
        // Pas de candidat (ou pas de GPS) : rien à comparer, inutile d'appeler le service IA.
        Optional<List<ServiceIAClient.Voisin>> voisins = candidats.isEmpty()
                ? Optional.of(List.of())
                : serviceIA.similaires(texte, dto.getLatitude(), dto.getLongitude(),
                        candidats.stream()
                                .map(c -> new ServiceIAClient.Candidat(c.getId(), texteDe(c),
                                        c.getLocalisation().getLatitude(), c.getLocalisation().getLongitude()))
                                .toList());
        List<ServiceIAClient.Voisin> doublons = voisins.orElse(List.of()).stream()
                .filter(ServiceIAClient.Voisin::doublonPotentiel)
                .toList();
        // Doublon POTENTIEL : la réclamation n'est ni bloquée ni fusionnée, un agent confirme (Human-in-the-Loop).
        rec.setEstDoublon(!doublons.isEmpty());

        Reclamation savedRec = reclamationRepository.save(rec);

        // ── Traces en base (audit + futur écran « Doublons à vérifier ») ─────────────────
        AnalyseIA nlp = new AnalyseIA();
        nlp.setReclamation(savedRec);
        nlp.setTypeAnalyse(TypeAnalyseIA.NLP);
        nlp.setDateAnalyse(LocalDateTime.now());
        if (analyseIA.isPresent()) {
            ServiceIAClient.ResultatAnalyse a = analyseIA.get();
            nlp.setResultat(enJson(a.brut()));
            nlp.setScoreConfiance(a.probabiliteMunicipal());
            nlp.setModeleUtilise("pfe-ai-service v1 (municipal + priorité + type d'incident)");
            if (Boolean.TRUE.equals(savedRec.getPerimetreAVerifier())) {
                log.info("Réclamation {} : périmètre municipal à vérifier par un humain (p = {})",
                        savedRec.getReference(), a.probabiliteMunicipal());
            }
        } else {
            nlp.setResultat("Service IA injoignable : priorité de secours (catégorie).");
            nlp.setScoreConfiance(0.5);
            nlp.setModeleUtilise("score de secours (microservice injoignable)");
        }
        analyseIARepository.save(nlp);

        if (voisins.isPresent()) {
            AnalyseIA analyseDoublon = new AnalyseIA();
            analyseDoublon.setReclamation(savedRec);
            analyseDoublon.setTypeAnalyse(TypeAnalyseIA.DETECTION_DOUBLON);
            analyseDoublon.setDateAnalyse(LocalDateTime.now());
            analyseDoublon.setDoublonDetecte(!doublons.isEmpty());
            analyseDoublon.setScoreConfiance(doublons.stream()
                    .mapToDouble(ServiceIAClient.Voisin::similarite).max().orElse(0));
            analyseDoublon.setResultat(enJson(Map.of(
                    "candidats_compares", candidats.size(),
                    "doublons_potentiels", doublons.stream().map(d -> Map.of(
                            "id", d.id(),
                            "reference", referenceDe(candidats, d.id()),
                            "distance_m", d.distanceM(),
                            "similarite", d.similarite())).toList())));
            analyseDoublon.setModeleUtilise("pfe-ai-service v1 (distance + similarité de texte)");
            analyseIARepository.save(analyseDoublon);
        }

        notifierAdminsNouvelleReclamation(savedRec, doublons.isEmpty() ? null
                : referenceDe(candidats, doublons.get(0).id()));

        assignerAutomatiquement(savedRec, categorie);

        return savedRec;
    }

    /**
     * Notifie tous les administrateurs actifs lors du dépôt d'une nouvelle
     * réclamation — leur permet de réagir vite même sans consulter le
     * dashboard en continu.
     */
    private void notifierAdminsNouvelleReclamation(Reclamation reclamation, String referenceDoublon) {
        List<Utilisateur> admins = utilisateurRepository.findByRole(Role.ADMIN);
        boolean perimetre = Boolean.TRUE.equals(reclamation.getPerimetreAVerifier());
        boolean horsPerimetreIA = perimetre && reclamation.getProbabiliteMunicipal() != null
                && reclamation.getProbabiliteMunicipal() < 0.5;
        String message = "\"" + reclamation.getTitre() + "\" (réf. " + reclamation.getReference()
                + ") vient d'être déposée par un citoyen."
                + (referenceDoublon != null
                    ? " Doublon potentiel de " + referenceDoublon + " (à vérifier)."
                    : "")
                + (perimetre
                    ? (horsPerimetreIA
                        ? " L'IA estime qu'elle ne relève peut-être pas de la commune (périmètre à vérifier)."
                        : " L'IA n'est pas sûre qu'elle relève de la commune (périmètre à vérifier).")
                    : "");

        boolean categorieDouteuse = Boolean.TRUE.equals(reclamation.getCategorieAVerifier());
        if (categorieDouteuse) {
            message += " Catégorie choisie : « " + reclamation.getCategorie().getNom()
                    + " » ; l'IA suggère « " + reclamation.getCategorieSuggereeNom() + " » (catégorie à vérifier).";
        }
        String titre = referenceDoublon != null ? "Nouvelle réclamation (doublon potentiel)"
                : perimetre ? "Nouvelle réclamation (périmètre à vérifier)"
                : categorieDouteuse ? "Nouvelle réclamation (catégorie à vérifier)"
                : "Nouvelle réclamation déposée";
        for (Utilisateur admin : admins) {
            notificationService.creer(admin, titre, message, reclamation.getId());
        }
    }

    // Méthodes pour le Dashboard Citoyen
    public List<Reclamation> getReclamationsParEmail(String email) {
        Utilisateur user = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return reclamationRepository.findByCitoyenId(user.getId());
    }

    /** Variante paginée utilisée par l'app mobile pour "Mes réclamations". */
    public Page<Reclamation> getReclamationsParEmailPage(String email, Pageable pageable) {
        Utilisateur user = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return reclamationRepository.findByCitoyenIdOrderByDateCreationDesc(user.getId(), pageable);
    }

    public List<Reclamation> getAllReclamations() {
        return reclamationRepository.findAll();
    }

    public Map<String, Long> getStatsCitoyen(String email) {
        Utilisateur user = utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        List<Reclamation> mesRecs = reclamationRepository.findByCitoyenId(user.getId());

        return Map.of(
                "total", (long) mesRecs.size(),
                "ouvertes", mesRecs.stream().filter(r -> r.getStatut() == StatutReclamation.OUVERTE).count(),
                "enCours", mesRecs.stream().filter(r -> r.getStatut() == StatutReclamation.EN_COURS).count(),
                "resolues", mesRecs.stream().filter(r -> r.getStatut() == StatutReclamation.RESOLUE).count(),
                "rejetees", mesRecs.stream().filter(r -> r.getStatut() == StatutReclamation.REJETEE).count()
        );
    }


    // --- Fonctions privées de logique "IA" ---

    /** Réclamations ouvertes ou en cours, récentes et proches : candidates au statut de doublon. */
    private List<Reclamation> chercherCandidatsDoublon(Double lat, Double lon) {
        if (lat == null || lon == null) {
            return List.of();
        }
        // Carré de ± RAYON autour du point (1° de latitude ≈ 111 km) ; le service IA affine.
        double dLat = RAYON_RECHERCHE_M / 111_320.0;
        double dLon = RAYON_RECHERCHE_M / (111_320.0 * Math.cos(Math.toRadians(lat)));
        return reclamationRepository.findCandidatsDoublon(
                List.of(StatutReclamation.OUVERTE, StatutReclamation.EN_COURS),
                LocalDateTime.now().minusDays(FENETRE_JOURS),
                lat - dLat, lat + dLat, lon - dLon, lon + dLon,
                PageRequest.of(0, MAX_CANDIDATS));
    }

    private static String texteDe(Reclamation r) {
        return (r.getTitre() == null ? "" : r.getTitre() + ". ")
                + (r.getDescription() == null ? "" : r.getDescription());
    }

    private static String referenceDe(List<Reclamation> liste, long id) {
        return liste.stream().filter(r -> r.getId() == id).map(Reclamation::getReference)
                .findFirst().orElse("#" + id);
    }

    private static String enJson(Object valeur) {
        try {
            return JSON.writeValueAsString(valeur);
        } catch (Exception e) {
            return String.valueOf(valeur);
        }
    }

    /** Priorité proposée par l'IA ; repli sur la catégorie si la valeur reçue est inconnue. */
    private Priorite prioriteDe(String valeurIA, Integer prioriteBase) {
        try {
            return Priorite.valueOf(valeurIA);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Priorité IA inattendue « {} » : priorité de secours utilisée.", valeurIA);
            return determinerPriorite(0.5, prioriteBase);
        }
    }

    /** Confiance minimale de l'incident reconnu pour signaler une erreur de catégorie. */
    private static final double SEUIL_CATEGORIE = 0.6;

    /**
     * Catégorie suggérée par l'IA quand elle diffère de celle choisie par le citoyen, sinon null.
     * Ne signale rien si l'admin n'a pas relié la catégorie choisie à une famille IA
     * (page Catégories), ni si l'IA n'est pas assez sûre de l'incident reconnu.
     */
    private CategorieReclamation categorieSuggeree(CategorieReclamation choisie, ServiceIAClient.ResultatAnalyse a) {
        String famille = a.familleSuggeree();
        if (famille == null || a.confianceFamille() < SEUIL_CATEGORIE
                || choisie.listeFamillesIA().isEmpty() || choisie.couvreFamille(famille)) {
            return null;
        }
        // Parmi les catégories qui couvrent cette famille, on préfère la plus spécialisée
        // (celle qui couvre le moins de familles) : « Eau » plutôt qu'une catégorie fourre-tout.
        return categorieRepository.findAll().stream()
                .filter(c -> c.couvreFamille(famille))
                .min(Comparator.comparingInt(c -> c.listeFamillesIA().size()))
                .orElse(null);
    }

    /** Priorité de SECOURS, utilisée seulement si le service IA est injoignable. */
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
    @Transactional
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

        reclamationRepository.save(rec);
        System.out.println("Annulation pour : " + id);


    }



    @Transactional
    public Affectation assignerAgent(Long reclamationId, Long agentId) {

        Reclamation reclamation = reclamationRepository.findById(reclamationId)
                .orElseThrow(() -> new RuntimeException("Réclamation introuvable avec l'id : " + reclamationId));

        Utilisateur agent = utilisateurRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable avec l'id : " + agentId));

        if (agent.getRole() != Role.AGENT) {
            throw new RuntimeException(
                    "L'utilisateur id=" + agentId + " n'est pas un AGENT. " +
                            "Seuls les utilisateurs avec le rôle AGENT peuvent être assignés."
            );
        }

        // Créer l'affectation
        Affectation affectation = new Affectation();
        affectation.setReclamation(reclamation);
        affectation.setAgent(agent);
        affectation.setModeAffectation(ModeAffectation.MANUELLE);
        affectation.setDateAffectation(LocalDateTime.now());
        affectation.setCommentaire("Affectation manuelle par l'administrateur");

        // Passer EN_COURS automatiquement si encore OUVERTE
        if (reclamation.getStatut() == StatutReclamation.OUVERTE) {
            StatutReclamation ancienStatut = reclamation.getStatut();
            reclamation.setStatut(StatutReclamation.EN_COURS);
            reclamationRepository.save(reclamation);

            HistoriqueStatut historique = new HistoriqueStatut();
            historique.setReclamation(reclamation);
            historique.setAncienStatut(ancienStatut);
            historique.setNouveauStatut(StatutReclamation.EN_COURS);
            historique.setDateChangement(LocalDateTime.now());
            historique.setCommentaire("Statut mis à jour automatiquement lors de l'affectation à l'agent : "
                    + agent.getPrenom() + " " + agent.getNom());
            historiqueRepository.save(historique);
        }

        Affectation affectationSauvee = affectationRepository.save(affectation);

        notificationService.creer(
                agent,
                "Nouvelle réclamation assignée",
                "La réclamation \"" + reclamation.getTitre() + "\" (réf. " + reclamation.getReference()
                        + ") vous a été assignée.",
                reclamation.getId()
        );

        return affectationSauvee;

    }


    @Transactional
    public Reclamation changerStatut(Long reclamationId, ChangerStatutRequestDTO dto) {

        Reclamation reclamation = reclamationRepository.findById(reclamationId)
                .orElseThrow(() -> new RuntimeException("Réclamation introuvable avec l'id : " + reclamationId));

        StatutReclamation nouveauStatut;
        try {
            nouveauStatut = StatutReclamation.valueOf(dto.getStatut().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                    "Statut invalide : '" + dto.getStatut() + "'. " +
                            "Valeurs acceptées : OUVERTE, EN_COURS, RESOLUE, REJETEE, FERMEE, ANNULEE"
            );
        }

        StatutReclamation ancienStatut = reclamation.getStatut();

        // Le citoyen doit pouvoir comprendre pourquoi sa réclamation est refusée.
        String motif = dto.getCommentaire() == null ? "" : dto.getCommentaire().trim();
        if (nouveauStatut == StatutReclamation.REJETEE && motif.isEmpty()) {
            throw new RuntimeException("Un motif est obligatoire pour rejeter une réclamation.");
        }

        // Sauvegarder dans l'historique
        HistoriqueStatut historique = new HistoriqueStatut();
        historique.setReclamation(reclamation);
        historique.setAncienStatut(ancienStatut);
        historique.setNouveauStatut(nouveauStatut);
        historique.setDateChangement(LocalDateTime.now());
        historique.setCommentaire(motif.isEmpty() ? dto.getCommentaire() : motif);
        historiqueRepository.save(historique);

        // Mettre à jour la réclamation
        reclamation.setStatut(nouveauStatut);
        Reclamation reclamationSauvee = reclamationRepository.save(reclamation);

        if (reclamation.getCitoyen() != null) {
            notificationService.creer(
                    reclamation.getCitoyen(),
                    MessagesStatut.titre(nouveauStatut),
                    MessagesStatut.message(reclamation, nouveauStatut, motif),
                    reclamation.getId()
            );
        }

        // Les doublons confirmés suivent la réclamation d'origine (résolue, rejetée ou fermée).
        doublonIAService.propagerStatut(reclamationSauvee, nouveauStatut, motif);

        return reclamationSauvee;
    }
    // Appelée par : GET /api/admin/reclamations/par-statut?statut=EN_COURS
// Nécessite dans ReclamationRepository : List<Reclamation> findByStatut(StatutReclamation statut);
    public List<Reclamation> getReclamationsParStatut(StatutReclamation statut) {
        return reclamationRepository.findByStatut(statut);
    }

    // Appelée par : GET /api/admin/reclamations/par-priorite?priorite=CRITIQUE
// Nécessite dans ReclamationRepository : List<Reclamation> findByPriorite(Priorite priorite);
    public List<Reclamation> getReclamationsParPriorite(Priorite priorite) {
        return reclamationRepository.findByPriorite(priorite);
    }

    // Appelée par : GET /api/admin/reclamations/urgentes — fonctionnalité IA
// Trie en mémoire par scoreUrgence décroissant, aucune query JPA nécessaire
    public List<Reclamation> getReclamationsUrgentes() {
        return reclamationRepository.findAll()
                .stream()
                .filter(r -> r.getScoreUrgence() != null)
                .sorted(Comparator.comparingDouble(Reclamation::getScoreUrgence).reversed())
                .collect(Collectors.toList());
    }
    /**
     * Après un changement de catégorie : affecte la réclamation à un agent du service de la NOUVELLE
     * catégorie (le moins chargé). Renvoie le nouvel agent, ou null si aucun agent n'a pu être affecté.
     */
    @Transactional
    public Utilisateur reaffecterSelonCategorie(Reclamation reclamation) {
        Utilisateur avant = dernierAgent(reclamation);
        assignerAutomatiquement(reclamation, reclamation.getCategorie());
        Utilisateur apres = dernierAgent(reclamation);
        boolean change = apres != null && (avant == null || !apres.getId().equals(avant.getId()));
        return change ? apres : null;
    }

    private Utilisateur dernierAgent(Reclamation r) {
        List<Affectation> a = affectationRepository.findByReclamationIdOrderByDateAffectationDesc(r.getId());
        return a.isEmpty() ? null : a.get(0).getAgent();
    }

    /**
     * L'affectation automatique a échoué : sans cela, la réclamation restait « Ouverte »
     * sans que personne ne le sache. Les administrateurs sont prévenus, avec la raison.
     */
    private void signalerAffectationManuelle(Reclamation reclamation, String raison) {
        for (Utilisateur admin : utilisateurRepository.findByRole(Role.ADMIN)) {
            notificationService.creer(admin, "Affectation manuelle nécessaire",
                    "La réclamation " + reclamation.getReference() + " n'a pas pu être affectée automatiquement : "
                            + raison + ". Affectez-la à un agent.",
                    reclamation.getId());
        }
    }

    @Transactional
    private void assignerAutomatiquement(Reclamation reclamation,
                                         CategorieReclamation categorie) {
        try {
            // Utiliser le nom complet pour éviter le conflit avec @Service Spring
            com.pfe.backend.entity.Service service = categorie.getServiceResponsable();

            if (service == null) {
                System.out.println("[AUTO-ASSIGN] Aucun service responsable " +
                        "pour la catégorie : " + categorie.getNom());
                signalerAffectationManuelle(reclamation, "la catégorie « " + categorie.getNom()
                        + " » n'est liée à aucun service");
                return;
            }

            if (!service.getActif()) {
                System.out.println("[AUTO-ASSIGN] Service inactif : " + service.getNom());
                signalerAffectationManuelle(reclamation, "le service « " + service.getNom() + " » est désactivé");
                return;
            }

            List<Utilisateur> agents = utilisateurRepository
                    .findByServiceIdAndRoleAndActifTrue(service.getId(), Role.AGENT);

            if (agents.isEmpty()) {
                System.out.println("[AUTO-ASSIGN] Aucun agent disponible " +
                        "dans le service : " + service.getNom());
                signalerAffectationManuelle(reclamation, "aucun agent actif dans le service « "
                        + service.getNom() + " »");
                return;
            }

            // Agent le moins chargé : dossiers non clos dont il est le responsable ACTUEL
            // (ouverts + en cours, sans compter les dossiers transférés à un collègue).
            // À égalité : celui qui a reçu le moins de dossiers au total, puis le plus ancien compte.
            java.util.Set<StatutReclamation> clos = java.util.EnumSet.of(StatutReclamation.RESOLUE,
                    StatutReclamation.REJETEE, StatutReclamation.FERMEE, StatutReclamation.ANNULEE);
            Utilisateur agentMoinsCharge = agents.stream()
                    .min(Comparator.<Utilisateur>comparingLong(agent ->
                                    affectationRepository.countDossiersActifs(agent.getId(), clos))
                            .thenComparingLong(agent -> affectationRepository.countByAgentId(agent.getId()))
                            .thenComparing(Utilisateur::getId))
                    .orElse(null);

            if (agentMoinsCharge == null) return;

            Affectation affectation = new Affectation();
            affectation.setReclamation(reclamation);
            affectation.setAgent(agentMoinsCharge);
            affectation.setModeAffectation(ModeAffectation.AUTOMATIQUE);
            affectation.setDateAffectation(LocalDateTime.now());
            affectation.setCommentaire(
                    "Assignation automatique — Service : " + service.getNom() +
                            " — Agent : " + agentMoinsCharge.getPrenom() +
                            " " + agentMoinsCharge.getNom()
            );
            affectationRepository.save(affectation);

            StatutReclamation ancienStatut = reclamation.getStatut();
            reclamation.setStatut(StatutReclamation.EN_COURS);
            reclamationRepository.save(reclamation);

            HistoriqueStatut historique = new HistoriqueStatut();
            historique.setReclamation(reclamation);
            historique.setAncienStatut(ancienStatut);
            historique.setNouveauStatut(StatutReclamation.EN_COURS);
            historique.setDateChangement(LocalDateTime.now());
            historique.setCommentaire(
                    "Assignation automatique à l'agent " +
                            agentMoinsCharge.getPrenom() + " " + agentMoinsCharge.getNom() +
                            " (Service : " + service.getNom() + ")"
            );
            historiqueRepository.save(historique);

            notificationService.creer(
                    agentMoinsCharge,
                    "Nouvelle réclamation assignée",
                    "La réclamation \"" + reclamation.getTitre() + "\" (réf. " + reclamation.getReference()
                            + ") vous a été assignée automatiquement.",
                    reclamation.getId()
            );

            System.out.println("[AUTO-ASSIGN] ✅ Réclamation " +
                    reclamation.getReference() + " assignée à " +
                    agentMoinsCharge.getPrenom() + " " + agentMoinsCharge.getNom());

        } catch (Exception e) {
            System.err.println("[AUTO-ASSIGN] ❌" +
                    " Erreur : " + e.getMessage());
        }
    }


    public List<HistoriqueStatut> getHistoriqueParCitoyen(Long reclamationId, String email) {
        // 1. Vérifier que la réclamation existe
        Reclamation rec = reclamationRepository.findById(reclamationId)
                .orElseThrow(() -> new RuntimeException("Réclamation introuvable."));

        // 2. Sécurité : vérifier que c'est bien la réclamation du citoyen connecté
        if (!rec.getCitoyen().getEmail().equals(email)) {
            throw new RuntimeException("Accès non autorisé à cette réclamation.");
        }

        // 3. Retourner l'historique trié du plus récent au plus ancien
        // historiqueRepository est déjà injecté dans ReclamationService
        return historiqueRepository
                .findByReclamationIdOrderByDateChangementDesc(reclamationId);
    }

}