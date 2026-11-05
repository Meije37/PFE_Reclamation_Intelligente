package com.pfe.backend.config;

import com.pfe.backend.entity.*;
import com.pfe.backend.entity.enums.*;
import com.pfe.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Données de démonstration réalistes à Nouakchott (pour la soutenance et les captures du mémoire).
 *
 * Activation (une seule ligne dans application-dev.properties) :  app.demo.enabled=true
 * Puis redémarrer Spring. Le chargement est fait UNE seule fois (repère : le citoyen
 * mohamed.ouldahmed@chikayti.mr) ; on peut laisser la ligne, rien ne sera dupliqué.
 * E-mails construits à partir des noms : prenom.nom@chikayti.mr (sans accents ni espaces).
 *
 * Contenu :
 *  - 3 wilayas (Nouakchott-Ouest, -Nord, -Sud) et leurs moughataas ;
 *  - les 5 services municipaux (réutilisés s'ils existent déjà, reconnus par leur nom) et une
 *    catégorie par service (réutilisée si une catégorie couvre déjà ce domaine) ;
 *  - 2 agents par service s'il en manque, 12 citoyens aux noms mauritaniens ;
 *  - ~70 réclamations des 45 derniers jours, en français et en arabe, réparties dans 9 moughataas
 *    (Tevragh Zeina, Ksar, Sebkha, El Mina, Arafat, Riyad, Teyarett, Dar Naim, Toujounine),
 *    avec statuts, historique, affectations, votes et avis des citoyens.
 *
 * Mots de passe : agents « Agent123 », citoyens « Citoyen123 ».
 */
@Component
@Order(100) // après la création de l'admin et les correctifs de schéma
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DonneesDemoRunner implements CommandLineRunner {

    /** Repère « déjà chargé » : le premier citoyen de démonstration (ancien ou nouveau format d'e-mail). */
    private static final List<String> REPERES = List.of("mohamed.ouldahmed@chikayti.mr", "demo.citoyen1@chikayti.mr");

    private final UtilisateurRepository utilisateurRepository;
    private final ZoneRepository zoneRepository;
    private final ServiceRepository serviceRepository;
    private final CategorieReclamationRepository categorieRepository;
    private final ReclamationRepository reclamationRepository;
    private final LocalisationRepository localisationRepository;
    private final AffectationRepository affectationRepository;
    private final HistoriqueStatutRepository historiqueRepository;
    private final VoteRepository voteRepository;
    private final EvaluationRepository evaluationRepository;
    private final PasswordEncoder encoder;

    /** Graine fixe : les mêmes données à chaque installation (démonstration reproductible). */
    private final Random hasard = new Random(42);

    // ── Géographie ─────────────────────────────────────────────────────────

    private record Moughataa(String nom, String wilaya, double lat, double lon) {}

    /** Centres approximatifs des moughataas (les réclamations sont dispersées autour, ±800 m). */
    private static final List<Moughataa> MOUGHATAAS = List.of(
            new Moughataa("Tevragh Zeina", "Nouakchott-Ouest", 18.1010, -15.9790),
            new Moughataa("Ksar", "Nouakchott-Ouest", 18.1030, -15.9550),
            new Moughataa("Sebkha", "Nouakchott-Ouest", 18.0740, -15.9750),
            new Moughataa("El Mina", "Nouakchott-Sud", 18.0560, -15.9690),
            new Moughataa("Arafat", "Nouakchott-Sud", 18.0580, -15.9380),
            new Moughataa("Riyad", "Nouakchott-Sud", 18.0230, -15.9420),
            new Moughataa("Teyarett", "Nouakchott-Nord", 18.1350, -15.9480),
            new Moughataa("Dar Naim", "Nouakchott-Nord", 18.1170, -15.9200),
            new Moughataa("Toujounine", "Nouakchott-Nord", 18.0880, -15.9100));

    private static final String[] LIEUX_FR = {
            "près du marché", "devant l'école primaire", "en face de la mosquée", "au carrefour principal",
            "près du centre de santé", "derrière la station-service", "dans la rue de la boulangerie",
            "près de l'arrêt de bus", "à côté de la pharmacie", "devant le terrain de football"};
    private static final String[] LIEUX_AR = {
            "قرب السوق", "أمام المدرسة الابتدائية", "مقابل المسجد", "عند الملتقى الرئيسي",
            "قرب المركز الصحي", "خلف محطة الوقود", "في شارع المخبزة", "قرب موقف الحافلات",
            "بجانب الصيدلية", "أمام ملعب كرة القدم"};

    // ── Incidents (famille, incident, gravité, titres et descriptions fr / ar) ──

    private record Modele(String famille, String incident, Priorite priorite,
                          String titreFr, String descFr, String titreAr, String descAr) {}

    private static final List<Modele> MODELES = List.of(
            new Modele(FamillesIA.EAU, "coupure_eau", Priorite.HAUTE,
                    "Coupure d'eau", "Plus d'eau dans le quartier depuis deux jours, %s.",
                    "انقطاع الماء", "انقطاع الماء عن الحي منذ يومين %s."),
            new Modele(FamillesIA.EAU, "fuite_conduite", Priorite.HAUTE,
                    "Fuite d'eau importante", "Une conduite fuit et l'eau coule dans la rue %s.",
                    "تسرب كبير للماء", "أنبوب ماء مكسور والماء يسيل في الشارع %s."),
            new Modele(FamillesIA.EAU, "egout_deborde", Priorite.HAUTE,
                    "Égout qui déborde", "Les eaux usées débordent et l'odeur est insupportable %s.",
                    "فيضان الصرف الصحي", "مياه الصرف الصحي تفيض والرائحة لا تطاق %s."),
            new Modele(FamillesIA.EAU, "regard_ouvert", Priorite.CRITIQUE,
                    "Regard d'égout ouvert", "Un regard est resté ouvert, des enfants passent à côté %s.",
                    "فتحة صرف مفتوحة", "فتحة صرف صحي مفتوحة والأطفال يمرون بجانبها %s."),
            new Modele(FamillesIA.EAU, "inondation", Priorite.HAUTE,
                    "Rue inondée", "Après la pluie, la rue est inondée et les maisons sont menacées %s.",
                    "شارع غارق بالمياه", "بعد المطر غرق الشارع والمنازل مهددة %s."),
            new Modele(FamillesIA.ELECTRICITE, "lampadaire", Priorite.MOYENNE,
                    "Lampadaire éteint", "Le lampadaire ne s'allume plus depuis une semaine, la rue est sombre %s.",
                    "عمود إنارة معطل", "عمود الإنارة لا يشتغل منذ أسبوع والشارع مظلم %s."),
            new Modele(FamillesIA.ELECTRICITE, "cable_terre", Priorite.CRITIQUE,
                    "Câble électrique tombé", "Un câble électrique est tombé par terre %s, c'est dangereux.",
                    "سلك كهربائي ساقط", "سلك كهربائي سقط على الأرض %s وهو خطير."),
            new Modele(FamillesIA.ELECTRICITE, "poteau_penche", Priorite.HAUTE,
                    "Poteau électrique penché", "Un poteau électrique penche dangereusement %s.",
                    "عمود كهرباء مائل", "عمود كهرباء مائل بشكل خطير %s."),
            new Modele(FamillesIA.VOIRIE, "nid_poule", Priorite.MOYENNE,
                    "Nid de poule", "Un grand trou sur la route abîme les voitures %s.",
                    "حفرة في الطريق", "حفرة كبيرة في الطريق تتلف السيارات %s."),
            new Modele(FamillesIA.VOIRIE, "route_ensablee", Priorite.MOYENNE,
                    "Route ensablée", "Le sable recouvre la route, les voitures restent bloquées %s.",
                    "طريق مغطى بالرمال", "الرمال تغطي الطريق والسيارات تعلق %s."),
            new Modele(FamillesIA.VOIRIE, "feu_signalisation", Priorite.HAUTE,
                    "Feu de circulation en panne", "Le feu ne fonctionne plus, risque d'accident %s.",
                    "إشارة مرور معطلة", "إشارة المرور لا تعمل وهناك خطر حوادث %s."),
            new Modele(FamillesIA.VOIRIE, "trottoir", Priorite.BASSE,
                    "Trottoir abîmé", "Le trottoir est cassé, difficile pour les piétons %s.",
                    "رصيف متضرر", "الرصيف مكسور ويصعب على المارة %s."),
            new Modele(FamillesIA.PROPRETE, "ordures", Priorite.MOYENNE,
                    "Ordures non ramassées", "Les ordures ne sont pas ramassées depuis une semaine %s.",
                    "نفايات متراكمة", "النفايات لم تُجمع منذ أسبوع %s."),
            new Modele(FamillesIA.PROPRETE, "depot_sauvage", Priorite.MOYENNE,
                    "Dépôt sauvage de déchets", "Des déchets sont jetés sur un terrain vide %s.",
                    "رمي عشوائي للنفايات", "يتم رمي النفايات في أرض فارغة %s."),
            new Modele(FamillesIA.PROPRETE, "dechets_brules", Priorite.HAUTE,
                    "Déchets brûlés", "Des déchets sont brûlés chaque soir, la fumée gêne les habitants %s.",
                    "حرق النفايات", "يتم حرق النفايات كل مساء والدخان يزعج السكان %s."),
            new Modele(FamillesIA.PROPRETE, "animal_mort", Priorite.MOYENNE,
                    "Animal mort sur la voie", "Un âne mort est sur la voie depuis hier %s.",
                    "حيوان نافق في الطريق", "حمار نافق في الطريق منذ أمس %s."),
            new Modele(FamillesIA.ESPACES, "arbre_tombe", Priorite.MOYENNE,
                    "Arbre tombé", "Un arbre est tombé et bloque le passage %s.",
                    "شجرة ساقطة", "شجرة سقطت وتسد الطريق %s."),
            new Modele(FamillesIA.ESPACES, "jeux_degrades", Priorite.BASSE,
                    "Jeux pour enfants abîmés", "Les jeux du jardin public sont cassés %s.",
                    "ألعاب أطفال متضررة", "ألعاب الحديقة العامة مكسورة %s."),
            new Modele(FamillesIA.ESPACES, "marche_insalubre", Priorite.MOYENNE,
                    "Marché insalubre", "Le marché est très sale, eaux stagnantes et déchets %s.",
                    "سوق غير نظيف", "السوق متسخ جدا ومياه راكدة ونفايات %s."));

    private static final Map<String, String> NOMS_SERVICES = Map.of(
            FamillesIA.EAU, "Eau et Assainissement",
            FamillesIA.ELECTRICITE, "Électricité et Éclairage public",
            FamillesIA.VOIRIE, "Voirie et Infrastructures",
            FamillesIA.PROPRETE, "Propreté et Gestion des Déchets",
            FamillesIA.ESPACES, "Espaces Verts et Marchés");

    private static final Map<String, String> NOMS_CATEGORIES = Map.of(
            FamillesIA.EAU, "Eau et Assainissement",
            FamillesIA.ELECTRICITE, "Éclairage public",
            FamillesIA.VOIRIE, "Routes et voirie",
            FamillesIA.PROPRETE, "Propreté et déchets",
            FamillesIA.ESPACES, "Espaces publics et marchés");

    private static final String[][] CITOYENS = {
            {"Mohamed", "Ould Ahmed"}, {"Aïchetou", "Mint Sidi"}, {"Sidi Mohamed", "Ould Cheikh"},
            {"Fatimetou", "Mint Mohamed"}, {"Ahmedou", "Ould Abdallahi"}, {"Mariem", "Mint Ely"},
            {"Cheikh", "Ould Brahim"}, {"Khadijetou", "Mint Ahmed Salem"}, {"Mamadou", "Ba"},
            {"Aminata", "Sow"}, {"Moussa", "Diallo"}, {"Vatimetou", "Mint Moctar"}};

    private static final String[][] AGENTS = {
            {"Abdallahi", "Ould Salem"}, {"Brahim", "Ould Mohamed Lemine"}, {"Oumar", "Kane"},
            {"Yacoub", "Ould Sidi"}, {"Hamadi", "Ould Mohamedou"}, {"Ousmane", "Sy"},
            {"Ely", "Ould Ahmedou"}, {"Boubacar", "Ndiaye"}, {"Mohamed Lemine", "Ould Bah"},
            {"Ismail", "Ould Ahmed Vall"}};

    // ── Chargement ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void run(String... args) {
        if (REPERES.stream().anyMatch(e -> utilisateurRepository.findByEmail(e).isPresent())) {
            log.info("[Démo] Données de démonstration déjà présentes : rien à faire.");
            return;
        }
        log.info("[Démo] Chargement des données de démonstration (Nouakchott)…");

        Map<String, Zone> zones = new HashMap<>();
        for (String w : List.of("Nouakchott-Ouest", "Nouakchott-Nord", "Nouakchott-Sud")) {
            zones.put(w, zoneRepository.findByNom(w).orElseGet(() -> {
                Zone z = new Zone();
                z.setNom(w);
                z.setTypeZone("Wilaya");
                z.setDescription("Wilaya de " + w);
                return zoneRepository.save(z);
            }));
        }

        Map<String, CategorieReclamation> categories = new HashMap<>();
        Map<String, List<Utilisateur>> agentsParFamille = new HashMap<>();
        int numeroAgent = 0;
        for (String famille : FamillesIA.TOUTES) {
            com.pfe.backend.entity.Service service = serviceDeFamille(famille, zones.get("Nouakchott-Ouest"));
            categories.put(famille, categorieDeFamille(famille, service));

            List<Utilisateur> agents = new ArrayList<>(utilisateurRepository
                    .findByServiceIdAndRoleAndActifTrue(service.getId(), Role.AGENT));
            while (agents.size() < 2 && numeroAgent < AGENTS.length) {
                String[] n = AGENTS[numeroAgent++];
                String mail = email(n[0], n[1]);
                if (utilisateurRepository.existsByEmail(mail)) continue;   // compte déjà présent : on ne le recrée pas
                agents.add(utilisateurRepository.save(Utilisateur.builder()
                        .prenom(n[0]).nom(n[1])
                        .email(mail)
                        .telephone(telephoneLibre(46000000L + numeroAgent * 1111L))
                        .motDePasse(encoder.encode("Agent123"))
                        .role(Role.AGENT).actif(true).service(service)
                        .build()));
            }
            agentsParFamille.put(famille, agents);
        }

        List<Utilisateur> citoyens = new ArrayList<>();
        for (int i = 0; i < CITOYENS.length; i++) {
            final int k = i;
            String mail = email(CITOYENS[i][0], CITOYENS[i][1]);
            long prefixe = i % 3 == 0 ? 2 : i % 3 == 1 ? 3 : 4;      // 2 = Chinguitel, 3 = Mattel, 4 = Mauritel
            citoyens.add(utilisateurRepository.findByEmail(mail).orElseGet(() -> utilisateurRepository.save(Utilisateur.builder()
                    .prenom(CITOYENS[k][0]).nom(CITOYENS[k][1])
                    .email(mail)
                    .telephone(telephoneLibre(prefixe * 10_000_000L + 2_213_579L + k * 13_579L))
                    .motDePasse(encoder.encode("Citoyen123"))
                    .role(Role.CITOYEN).actif(true)
                    .build())));
        }

        int total = 72;
        for (int i = 0; i < total; i++) {
            creerReclamation(i, citoyens, categories, agentsParFamille);
        }
        log.info("[Démo] {} réclamations, {} citoyens et les agents manquants ont été créés.", total, citoyens.size());
        log.info("[Démo] Connexion citoyen : mohamed.ouldahmed@chikayti.mr / Citoyen123 — agents : voir la page Services (mot de passe Agent123)");
    }

    private void creerReclamation(int i, List<Utilisateur> citoyens, Map<String, CategorieReclamation> categories,
                                  Map<String, List<Utilisateur>> agentsParFamille) {
        Modele m = MODELES.get(hasard.nextInt(MODELES.size()));
        Moughataa q = MOUGHATAAS.get(i % MOUGHATAAS.size());
        boolean arabe = hasard.nextInt(100) < 35;            // environ un tiers en arabe
        int lieu = hasard.nextInt(LIEUX_FR.length);
        Utilisateur citoyen = citoyens.get(hasard.nextInt(citoyens.size()));

        // Date : sur les 45 derniers jours, plus de réclamations récentes que d'anciennes.
        int joursAvant = (int) Math.floor(Math.pow(hasard.nextDouble(), 1.6) * 45);
        LocalDateTime creation = LocalDateTime.now().minusDays(joursAvant)
                .withHour(7 + hasard.nextInt(14)).withMinute(hasard.nextInt(60)).withSecond(0).withNano(0);
        if (creation.isAfter(LocalDateTime.now())) creation = LocalDateTime.now().minusHours(1);

        Localisation loc = new Localisation();
        loc.setLatitude(q.lat() + (hasard.nextDouble() - 0.5) * 0.016);
        loc.setLongitude(q.lon() + (hasard.nextDouble() - 0.5) * 0.016);
        loc.setAdresse(LIEUX_FR[lieu].substring(0, 1).toUpperCase() + LIEUX_FR[lieu].substring(1));
        loc.setQuartier(q.nom());
        loc.setVille("Nouakchott");
        loc = localisationRepository.save(loc);

        Reclamation r = new Reclamation();
        r.setReference("REC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        r.setTitre(arabe ? m.titreAr() : m.titreFr());
        r.setDescription(arabe ? String.format(m.descAr(), LIEUX_AR[lieu]) + " - " + q.nom()
                : String.format(m.descFr(), LIEUX_FR[lieu] + " à " + q.nom()));
        r.setCitoyen(citoyen);
        r.setCategorie(categories.get(m.famille()));
        r.setLocalisation(loc);
        r.setDateCreation(creation);
        r.setPriorite(m.priorite());
        r.setScoreUrgence(switch (m.priorite()) {
            case BASSE -> 0.25; case MOYENNE -> 0.45; case HAUTE -> 0.70; case CRITIQUE -> 0.95;
        });
        // Ce que l'IA aurait proposé (cohérent avec la carte « Analyse IA »).
        r.setPrioriteIA(m.priorite());
        r.setAlerteCritiqueIA(m.priorite() == Priorite.CRITIQUE && hasard.nextBoolean());
        r.setIncidentIA(m.incident());
        r.setFamilleSuggereeIA(m.famille());
        r.setProbabiliteMunicipal(0.93 + hasard.nextDouble() * 0.06);
        r.setConfiancePrioriteIA(0.55 + hasard.nextDouble() * 0.35);
        r.setPerimetreAVerifier(false);
        r.setCategorieAVerifier(false);
        r.setPrioriteValidee(false);
        r.setEstDoublon(false);

        // Statut : les anciennes sont plus souvent résolues, les récentes plus souvent ouvertes.
        int tirage = hasard.nextInt(100);
        StatutReclamation statut;
        if (joursAvant <= 1) statut = tirage < 60 ? StatutReclamation.OUVERTE : StatutReclamation.EN_COURS;
        else if (joursAvant <= 7) statut = tirage < 20 ? StatutReclamation.OUVERTE
                : tirage < 75 ? StatutReclamation.EN_COURS : StatutReclamation.RESOLUE;
        else statut = tirage < 10 ? StatutReclamation.EN_COURS
                : tirage < 88 ? StatutReclamation.RESOLUE : StatutReclamation.REJETEE;
        r.setStatut(StatutReclamation.OUVERTE);
        r = reclamationRepository.save(r);

        // Prise en charge : affectation + historique.
        if (statut != StatutReclamation.OUVERTE) {
            List<Utilisateur> agents = agentsParFamille.get(m.famille());
            Utilisateur agent = agents.isEmpty() ? null : agents.get(hasard.nextInt(agents.size()));
            LocalDateTime prise = avant(creation.plusHours(1 + hasard.nextInt(20)));
            if (agent != null) {
                Affectation a = new Affectation();
                a.setReclamation(r);
                a.setAgent(agent);
                a.setModeAffectation(ModeAffectation.AUTOMATIQUE);
                a.setDateAffectation(prise);
                a.setCommentaire("Assignation automatique (données de démonstration)");
                affectationRepository.save(a);
            }
            historiser(r, StatutReclamation.OUVERTE, StatutReclamation.EN_COURS, prise,
                    agent != null ? "Assignation automatique à l'agent " + agent.getPrenom() + " " + agent.getNom() : null);

            if (statut == StatutReclamation.RESOLUE || statut == StatutReclamation.REJETEE) {
                LocalDateTime fin = avant(prise.plusHours(6 + hasard.nextInt(110)));
                historiser(r, StatutReclamation.EN_COURS, statut, fin, statut == StatutReclamation.REJETEE
                        ? "Problème situé sur un terrain privé : il ne relève pas des services municipaux."
                        : "Intervention réalisée, le problème est réglé.");
                // Environ deux réclamations résolues sur trois reçoivent un avis du citoyen.
                if (statut == StatutReclamation.RESOLUE && hasard.nextInt(100) < 65) {
                    Evaluation e = new Evaluation();
                    e.setReclamation(r);
                    e.setCitoyen(citoyen);
                    e.setAgent(agent);
                    int note = new int[]{5, 5, 4, 4, 4, 3, 3, 2, 5, 4, 1}[hasard.nextInt(11)];
                    e.setNote(note);
                    e.setCommentaire(note >= 4 ? "Merci, intervention rapide."
                            : note == 3 ? "Réglé, mais un peu lent." : "Le problème est revenu après deux jours.");
                    e.setDateEvaluation(avant(fin.plusHours(2 + hasard.nextInt(30))));
                    evaluationRepository.save(e);
                }
            }
            r.setStatut(statut);
            r = reclamationRepository.save(r);
        }

        // Soutien des voisins : plus de votes pour les problèmes graves.
        int maxVotes = switch (m.priorite()) { case CRITIQUE -> 9; case HAUTE -> 6; case MOYENNE -> 4; case BASSE -> 2; };
        List<Utilisateur> voisins = new ArrayList<>(citoyens);
        voisins.remove(citoyen);
        Collections.shuffle(voisins, hasard);
        int nbVotes = hasard.nextInt(maxVotes + 1);
        for (int k = 0; k < nbVotes && k < voisins.size(); k++) {
            Vote v = new Vote();
            v.setReclamation(r);
            v.setCitoyen(voisins.get(k));
            v.setValeur(1);
            v.setDateVote(avant(creation.plusHours(1 + hasard.nextInt(48))));
            voteRepository.save(v);
        }
    }

    // ── Outils ─────────────────────────────────────────────────────────────

    /** « Aïchetou », « Mint Sidi » → aichetou.mintsidi@chikayti.mr */
    /** Premier numéro à 8 chiffres libre à partir du candidat (évite le conflit avec un compte déjà créé). */
    private String telephoneLibre(long candidat) {
        long n = candidat;
        while (utilisateurRepository.existsByTelephone(String.valueOf(n))) n += 7;
        return String.valueOf(n);
    }

    private static String email(String prenom, String nom) {
        return slug(prenom) + "." + slug(nom) + "@chikayti.mr";
    }

    private static String slug(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z]", "");
    }

    /** Service municipal de cette famille : réutilisé s'il existe (reconnu par son nom), sinon créé. */
    private com.pfe.backend.entity.Service serviceDeFamille(String famille, Zone zoneParDefaut) {
        return serviceRepository.findAll().stream()
                .filter(s -> FamillesIA.depuisNomService(s.getNom()).contains(famille))
                .findFirst()
                .map(s -> {
                    if (!Boolean.TRUE.equals(s.getActif())) {
                        s.setActif(true);
                        return serviceRepository.save(s);
                    }
                    return s;
                })
                .orElseGet(() -> {
                    com.pfe.backend.entity.Service s = new com.pfe.backend.entity.Service();
                    s.setNom(NOMS_SERVICES.get(famille));
                    s.setDescription("Service municipal de Nouakchott — " + NOMS_SERVICES.get(famille).toLowerCase());
                    s.setEmailService(famille.toLowerCase().replace('_', '.') + "@chikayti.mr");
                    s.setActif(true);
                    s.setZone(zoneParDefaut);
                    return serviceRepository.save(s);
                });
    }

    /** Catégorie de cette famille : la plus spécialisée existante, sinon créée et liée au service. */
    private CategorieReclamation categorieDeFamille(String famille, com.pfe.backend.entity.Service service) {
        return categorieRepository.findAll().stream()
                .filter(c -> c.couvreFamille(famille))
                .min(Comparator.comparingInt(c -> c.listeFamillesIA().size()))
                .orElseGet(() -> {
                    CategorieReclamation c = new CategorieReclamation();
                    c.setNom(NOMS_CATEGORIES.get(famille));
                    c.setDescription("Signalements : " + NOMS_CATEGORIES.get(famille).toLowerCase() + ".");
                    c.setPrioriteParDefaut(famille.equals(FamillesIA.EAU) || famille.equals(FamillesIA.ELECTRICITE) ? 4 : 2);
                    c.setServiceResponsable(service);
                    return categorieRepository.save(c);
                });
    }

    private void historiser(Reclamation r, StatutReclamation ancien, StatutReclamation nouveau,
                            LocalDateTime date, String commentaire) {
        HistoriqueStatut h = new HistoriqueStatut();
        h.setReclamation(r);
        h.setAncienStatut(ancien);
        h.setNouveauStatut(nouveau);
        h.setDateChangement(date);
        h.setCommentaire(commentaire);
        historiqueRepository.save(h);
    }

    /** Jamais dans le futur. */
    private static LocalDateTime avant(LocalDateTime d) {
        LocalDateTime maintenant = LocalDateTime.now().minusMinutes(5);
        return d.isAfter(maintenant) ? maintenant : d;
    }
}
