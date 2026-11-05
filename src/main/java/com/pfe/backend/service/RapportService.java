package com.pfe.backend.service;

import com.pfe.backend.entity.*;
import com.pfe.backend.entity.enums.Priorite;
import com.pfe.backend.entity.enums.StatutReclamation;
import com.pfe.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Rapport d'activité sur une période (date de dépôt des réclamations) : pour l'administration
 * (toutes les réclamations) ou pour un agent (seulement celles dont il est le responsable actuel).
 *
 * Les MÊMES chiffres alimentent :
 *  - le rapport imprimable / PDF (JSON : synthese) ;
 *  - le classeur Excel (.xlsx, plusieurs onglets : synthèse, répartitions, agents, liste détaillée).
 *
 * Indicateurs de délai :
 *  - prise en charge = dépôt → premier passage « En cours » (historique des statuts) ;
 *  - résolution      = dépôt → passage « Résolue ».
 */
@Service
@RequiredArgsConstructor
public class RapportService {

    private final ReclamationRepository reclamationRepository;
    private final HistoriqueStatutRepository historiqueRepository;
    private final AffectationRepository affectationRepository;
    private final EvaluationRepository evaluationRepository;
    private final VoteRepository voteRepository;

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter JOUR_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final List<Priorite> ORDRE_PRIORITES =
            List.of(Priorite.CRITIQUE, Priorite.HAUTE, Priorite.MOYENNE, Priorite.BASSE);

    // ── Données de la période ──────────────────────────────────────────────

    /** Tout ce qu'il faut pour une période : réclamations + historique, agents, avis et votes indexés. */
    private record Donnees(LocalDate du, LocalDate au, List<Reclamation> reclamations,
                           Map<Long, List<HistoriqueStatut>> historique, Map<Long, Utilisateur> agent,
                           Map<Long, Evaluation> avis, Map<Long, Long> votes) {

        Optional<LocalDateTime> premier(Reclamation r, StatutReclamation statut) {
            return historique.getOrDefault(r.getId(), List.of()).stream()
                    .filter(h -> h.getNouveauStatut() == statut && h.getDateChangement() != null)
                    .map(HistoriqueStatut::getDateChangement)
                    .min(Comparator.naturalOrder());
        }

        /** Heures entre le dépôt et le premier passage au statut donné (vide si jamais atteint). */
        Optional<Double> heuresJusqua(Reclamation r, StatutReclamation statut) {
            if (r.getDateCreation() == null) return Optional.empty();
            return premier(r, statut)
                    .filter(d -> !d.isBefore(r.getDateCreation()))
                    .map(d -> Duration.between(r.getDateCreation(), d).toMinutes() / 60.0);
        }
    }

    private Donnees donnees(LocalDate du, LocalDate au) {
        return donnees(du, au, null);
    }

    /** agentId non null : on ne garde que les réclamations dont cet agent est le responsable actuel. */
    private Donnees donnees(LocalDate du, LocalDate au, Long agentId) {
        LocalDateTime debut = du.atStartOfDay();
        LocalDateTime fin = au.plusDays(1).atStartOfDay();
        List<Reclamation> recs = reclamationRepository.findAll().stream()
                .filter(r -> r.getDateCreation() != null
                        && !r.getDateCreation().isBefore(debut) && r.getDateCreation().isBefore(fin))
                .sorted(Comparator.comparing(Reclamation::getDateCreation).reversed())
                .toList();
        Set<Long> ids = recs.stream().map(Reclamation::getId).collect(Collectors.toSet());

        Map<Long, List<HistoriqueStatut>> historique = historiqueRepository.findAll().stream()
                .filter(h -> h.getReclamation() != null && ids.contains(h.getReclamation().getId()))
                .collect(Collectors.groupingBy(h -> h.getReclamation().getId()));

        // Agent responsable = affectation la plus récente.
        Map<Long, Utilisateur> agent = new HashMap<>();
        Map<Long, LocalDateTime> dateAffectation = new HashMap<>();
        for (Affectation a : affectationRepository.findAll()) {
            if (a.getReclamation() == null || a.getAgent() == null) continue;
            Long id = a.getReclamation().getId();
            if (!ids.contains(id)) continue;
            LocalDateTime d = a.getDateAffectation() != null ? a.getDateAffectation() : LocalDateTime.MIN;
            if (!dateAffectation.containsKey(id) || d.isAfter(dateAffectation.get(id))) {
                dateAffectation.put(id, d);
                agent.put(id, a.getAgent());
            }
        }

        if (agentId != null) {
            recs = recs.stream()
                    .filter(r -> agent.get(r.getId()) != null && agentId.equals(agent.get(r.getId()).getId()))
                    .toList();
        }

        Map<Long, Evaluation> avis = evaluationRepository.findAll().stream()
                .filter(e -> e.getReclamation() != null && ids.contains(e.getReclamation().getId()))
                .collect(Collectors.toMap(e -> e.getReclamation().getId(), Function.identity(), (a, b) -> a));

        Map<Long, Long> votes = voteRepository.findAll().stream()
                .filter(v -> v.getReclamation() != null && ids.contains(v.getReclamation().getId()))
                .collect(Collectors.groupingBy(v -> v.getReclamation().getId(), Collectors.counting()));

        return new Donnees(du, au, recs, historique, agent, avis, votes);
    }

    // ── Synthèse (JSON pour le rapport imprimable) ─────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> synthese(LocalDate du, LocalDate au) {
        return synthese(du, au, null);
    }

    /** Rapport personnel d'un agent (agent non null) ou rapport de toute la commune (agent null). */
    @Transactional(readOnly = true)
    public Map<String, Object> synthese(LocalDate du, LocalDate au, Utilisateur agentRapport) {
        Donnees d = donnees(du, au, agentRapport != null ? agentRapport.getId() : null);
        List<Reclamation> recs = d.reclamations();

        Map<String, Object> s = new LinkedHashMap<>();
        if (agentRapport != null) {
            s.put("agent", nomComplet(agentRapport));
            s.put("service", agentRapport.getService() != null ? agentRapport.getService().getNom() : null);
        }
        s.put("du", du.toString());
        s.put("au", au.toString());
        s.put("genereLe", LocalDateTime.now().format(JOUR_HEURE));
        s.put("indicateurs", indicateurs(d));
        s.put("parStatut", parStatut(recs));
        s.put("parPriorite", parPriorite(recs));
        s.put("parCategorie", parCategorie(recs));
        s.put("parMoughataa", parMoughataa(recs));
        s.put("parAgent", agentRapport != null ? List.of() : parAgent(d));
        s.put("evolution", evolution(d));
        s.put("plusSoutenues", recs.stream()
                .filter(r -> r.getStatut() != null && !r.getStatut().estClos())
                .sorted(Comparator.comparingLong((Reclamation r) -> d.votes().getOrDefault(r.getId(), 0L)).reversed())
                .limit(5)
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("reference", r.getReference());
                    m.put("titre", r.getTitre());
                    m.put("moughataa", moughataaDe(r));
                    m.put("priorite", libelle(r.getPriorite()));
                    m.put("votes", d.votes().getOrDefault(r.getId(), 0L));
                    return m;
                }).toList());
        return s;
    }

    private Map<String, Object> indicateurs(Donnees d) {
        List<Reclamation> recs = d.reclamations();
        long total = recs.size();
        Map<StatutReclamation, Long> n = recs.stream().filter(r -> r.getStatut() != null)
                .collect(Collectors.groupingBy(Reclamation::getStatut, Collectors.counting()));
        long resolues = n.getOrDefault(StatutReclamation.RESOLUE, 0L);
        long annulees = n.getOrDefault(StatutReclamation.ANNULEE, 0L);
        long traitables = total - annulees;

        List<Evaluation> avis = new ArrayList<>(d.avis().values());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("ouvertes", n.getOrDefault(StatutReclamation.OUVERTE, 0L));
        m.put("enCours", n.getOrDefault(StatutReclamation.EN_COURS, 0L));
        m.put("resolues", resolues);
        m.put("rejetees", n.getOrDefault(StatutReclamation.REJETEE, 0L));
        m.put("fermeesOuAnnulees", n.getOrDefault(StatutReclamation.FERMEE, 0L) + annulees);
        m.put("tauxResolution", traitables == 0 ? null : arrondi(100.0 * resolues / traitables));
        m.put("critiques", recs.stream().filter(r -> r.getPriorite() == Priorite.CRITIQUE).count());
        m.put("delaiPriseEnChargeH", moyenne(recs.stream()
                .map(r -> d.heuresJusqua(r, StatutReclamation.EN_COURS)).flatMap(Optional::stream).toList()));
        m.put("delaiResolutionH", moyenne(recs.stream()
                .map(r -> d.heuresJusqua(r, StatutReclamation.RESOLUE)).flatMap(Optional::stream).toList()));
        m.put("noteMoyenne", moyenne(avis.stream().map(e -> (double) e.getNote()).toList()));
        m.put("nombreAvis", (long) avis.size());
        m.put("doublons", recs.stream().filter(r -> Boolean.TRUE.equals(r.getEstDoublon()) || r.getDoublonDe() != null).count());
        m.put("soutiens", d.votes().values().stream().mapToLong(Long::longValue).sum());
        m.put("enArabe", recs.stream().filter(r -> contientArabe(r.getTitre()) || contientArabe(r.getDescription())).count());
        return m;
    }

    private List<Map<String, Object>> parStatut(List<Reclamation> recs) {
        long total = recs.size();
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (StatutReclamation st : StatutReclamation.values()) {
            long nb = recs.stream().filter(r -> r.getStatut() == st).count();
            if (nb == 0) continue;
            lignes.add(ligne("code", st.name(), "libelle", majuscule(st.getLibelle()),
                    "nombre", nb, "pourcentage", pct(nb, total)));
        }
        return lignes;
    }

    private List<Map<String, Object>> parPriorite(List<Reclamation> recs) {
        long total = recs.size();
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (Priorite p : ORDRE_PRIORITES) {
            long nb = recs.stream().filter(r -> r.getPriorite() == p).count();
            lignes.add(ligne("code", p.name(), "libelle", libelle(p), "nombre", nb, "pourcentage", pct(nb, total)));
        }
        return lignes;
    }

    private List<Map<String, Object>> parCategorie(List<Reclamation> recs) {
        Map<String, List<Reclamation>> g = recs.stream().collect(Collectors.groupingBy(
                r -> r.getCategorie() != null ? r.getCategorie().getNom() : "Sans catégorie"));
        return g.entrySet().stream()
                .map(e -> {
                    long nb = e.getValue().size();
                    long res = e.getValue().stream().filter(r -> r.getStatut() == StatutReclamation.RESOLUE).count();
                    return ligne("nom", e.getKey(), "total", nb, "resolues", res,
                            "aTraiter", e.getValue().stream().filter(RapportService::aTraiter).count(),
                            "tauxResolution", pct(res, nb));
                })
                .sorted(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("total")).reversed())
                .toList();
    }

    private List<Map<String, Object>> parMoughataa(List<Reclamation> recs) {
        Map<String, List<Reclamation>> g = recs.stream().collect(Collectors.groupingBy(this::moughataaDe));
        return g.entrySet().stream()
                .map(e -> ligne("nom", e.getKey(), "total", (long) e.getValue().size(),
                        "aTraiter", e.getValue().stream().filter(RapportService::aTraiter).count(),
                        "graves", e.getValue().stream().filter(r -> aTraiter(r)
                                && (r.getPriorite() == Priorite.HAUTE || r.getPriorite() == Priorite.CRITIQUE)).count(),
                        "resolues", e.getValue().stream().filter(r -> r.getStatut() == StatutReclamation.RESOLUE).count()))
                .sorted(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("total")).reversed())
                .toList();
    }

    private List<Map<String, Object>> parAgent(Donnees d) {
        Map<Long, List<Reclamation>> g = new LinkedHashMap<>();
        Map<Long, Utilisateur> agents = new HashMap<>();
        for (Reclamation r : d.reclamations()) {
            Utilisateur a = d.agent().get(r.getId());
            if (a == null) continue;
            agents.put(a.getId(), a);
            g.computeIfAbsent(a.getId(), k -> new ArrayList<>()).add(r);
        }
        return g.entrySet().stream().map(e -> {
            Utilisateur a = agents.get(e.getKey());
            List<Reclamation> rs = e.getValue();
            List<Double> notes = rs.stream().map(r -> d.avis().get(r.getId())).filter(Objects::nonNull)
                    .map(ev -> (double) ev.getNote()).toList();
            return ligne(
                    "agent", nomComplet(a),
                    "service", a.getService() != null ? a.getService().getNom() : "—",
                    "assignees", (long) rs.size(),
                    "enCours", rs.stream().filter(r -> r.getStatut() == StatutReclamation.EN_COURS).count(),
                    "resolues", rs.stream().filter(r -> r.getStatut() == StatutReclamation.RESOLUE).count(),
                    "delaiResolutionH", moyenne(rs.stream().map(r -> d.heuresJusqua(r, StatutReclamation.RESOLUE))
                            .flatMap(Optional::stream).toList()),
                    "noteMoyenne", moyenne(notes),
                    "nombreAvis", (long) notes.size());
        }).sorted(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("assignees")).reversed()).toList();
    }

    /** Réclamations déposées par jour (période ≤ 62 jours), sinon par mois. */
    private List<Map<String, Object>> evolution(Donnees d) {
        long jours = ChronoUnit.DAYS.between(d.du(), d.au()) + 1;
        List<Map<String, Object>> points = new ArrayList<>();
        if (jours <= 62) {
            Map<LocalDate, Long> parJour = d.reclamations().stream().collect(
                    Collectors.groupingBy(r -> r.getDateCreation().toLocalDate(), Collectors.counting()));
            for (LocalDate j = d.du(); !j.isAfter(d.au()); j = j.plusDays(1)) {
                points.add(ligne("libelle", j.format(DateTimeFormatter.ofPattern("dd/MM")), "total", parJour.getOrDefault(j, 0L)));
            }
        } else {
            Map<String, Long> parMois = d.reclamations().stream().collect(Collectors.groupingBy(
                    r -> r.getDateCreation().format(DateTimeFormatter.ofPattern("yyyy-MM")), TreeMap::new, Collectors.counting()));
            parMois.forEach((mois, nb) -> points.add(ligne("libelle", mois, "total", nb)));
        }
        return points;
    }

    // ── Excel ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] excel(LocalDate du, LocalDate au) throws IOException {
        return excel(du, au, null);
    }

    @Transactional(readOnly = true)
    public byte[] excel(LocalDate du, LocalDate au, Utilisateur agentRapport) throws IOException {
        Map<String, Object> s = synthese(du, au, agentRapport);
        Donnees d = donnees(du, au, agentRapport != null ? agentRapport.getId() : null);

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);

            // 1. Synthèse
            Sheet syn = wb.createSheet("Synthèse");
            int l = 0;
            Row titre = syn.createRow(l++);
            ecrire(titre, 0, agentRapport != null
                    ? "Chikayti — Rapport d'activité de " + nomComplet(agentRapport)
                    : "Chikayti — Rapport d'activité des réclamations citoyennes", st.titre);
            syn.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
            String perimetre = agentRapport != null
                    ? "Agent" + (agentRapport.getService() != null ? " · Service " + agentRapport.getService().getNom() : "")
                    : "Commune de Nouakchott";
            ecrire(syn.createRow(l++), 0, perimetre + " · Période du " + du.format(JOUR) + " au " + au.format(JOUR), st.normal);
            ecrire(syn.createRow(l++), 0, "Généré le " + s.get("genereLe"), st.discret);
            l++;
            @SuppressWarnings("unchecked")
            Map<String, Object> ind = (Map<String, Object>) s.get("indicateurs");
            Object[][] kpis = {
                    {"Réclamations déposées", ind.get("total")},
                    {"Ouvertes (en attente)", ind.get("ouvertes")},
                    {"En cours de traitement", ind.get("enCours")},
                    {"Résolues", ind.get("resolues")},
                    {"Rejetées", ind.get("rejetees")},
                    {"Fermées ou annulées", ind.get("fermeesOuAnnulees")},
                    {"Taux de résolution (%)", ind.get("tauxResolution")},
                    {"Réclamations critiques", ind.get("critiques")},
                    {"Délai moyen de prise en charge (heures)", ind.get("delaiPriseEnChargeH")},
                    {"Délai moyen de résolution (heures)", ind.get("delaiResolutionH")},
                    {"Note moyenne des citoyens (sur 5)", ind.get("noteMoyenne")},
                    {"Nombre d'avis citoyens", ind.get("nombreAvis")},
                    {"Doublons détectés", ind.get("doublons")},
                    {"Soutiens (votes) des citoyens", ind.get("soutiens")},
                    {"Réclamations rédigées en arabe", ind.get("enArabe")},
            };
            Row entete = syn.createRow(l++);
            ecrire(entete, 0, "Indicateur", st.entete);
            ecrire(entete, 1, "Valeur", st.entete);
            for (Object[] k : kpis) {
                Row r = syn.createRow(l++);
                ecrire(r, 0, k[0], st.normal);
                ecrire(r, 1, k[1] == null ? "—" : k[1], st.nombre);
            }
            syn.setColumnWidth(0, 45 * 256);
            syn.setColumnWidth(1, 14 * 256);

            // 2. Répartitions
            tableau(wb, st, "Statuts", new String[]{"Statut", "Nombre", "Part (%)"},
                    lignes(s, "parStatut", "libelle", "nombre", "pourcentage"));
            tableau(wb, st, "Priorités", new String[]{"Priorité", "Nombre", "Part (%)"},
                    lignes(s, "parPriorite", "libelle", "nombre", "pourcentage"));
            tableau(wb, st, "Catégories", new String[]{"Catégorie", "Total", "Résolues", "À traiter", "Taux de résolution (%)"},
                    lignes(s, "parCategorie", "nom", "total", "resolues", "aTraiter", "tauxResolution"));
            tableau(wb, st, "Moughataas", new String[]{"Moughataa", "Total", "À traiter", "Dont graves", "Résolues"},
                    lignes(s, "parMoughataa", "nom", "total", "aTraiter", "graves", "resolues"));
            if (agentRapport == null) {
                tableau(wb, st, "Agents", new String[]{"Agent", "Service", "Assignées", "En cours", "Résolues",
                                "Délai moyen de résolution (h)", "Note moyenne", "Avis"},
                        lignes(s, "parAgent", "agent", "service", "assignees", "enCours", "resolues",
                                "delaiResolutionH", "noteMoyenne", "nombreAvis"));
            }
            tableau(wb, st, "Évolution", new String[]{"Période", "Réclamations déposées"},
                    lignes(s, "evolution", "libelle", "total"));

            // 3. Liste détaillée
            List<Object[]> detail = new ArrayList<>();
            for (Reclamation r : d.reclamations()) {
                Utilisateur ag = d.agent().get(r.getId());
                Evaluation ev = d.avis().get(r.getId());
                detail.add(new Object[]{
                        r.getReference(),
                        r.getDateCreation() != null ? r.getDateCreation().format(JOUR_HEURE) : "",
                        r.getTitre(),
                        r.getDescription(),
                        r.getCategorie() != null ? r.getCategorie().getNom() : "",
                        libelle(r.getPriorite()),
                        r.getStatut() != null ? majuscule(r.getStatut().getLibelle()) : "",
                        moughataaDe(r),
                        r.getCitoyen() != null ? nomComplet(r.getCitoyen()) : "",
                        ag != null ? nomComplet(ag) : "",
                        d.votes().getOrDefault(r.getId(), 0L),
                        ev != null ? ev.getNote() : null,
                        d.heuresJusqua(r, StatutReclamation.RESOLUE).map(RapportService::arrondi).orElse(null),
                });
            }
            Sheet liste = tableau(wb, st, "Réclamations", new String[]{"Référence", "Déposée le", "Titre", "Description",
                    "Catégorie", "Priorité", "Statut", "Moughataa", "Citoyen", "Agent", "Soutiens", "Note citoyen",
                    "Délai de résolution (h)"}, detail);
            liste.setColumnWidth(2, 32 * 256);
            liste.setColumnWidth(3, 60 * 256);

            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Styles partagés du classeur. */
    private static class Styles {
        final CellStyle titre, entete, normal, nombre, discret;

        Styles(Workbook wb) {
            Font gras = wb.createFont();
            gras.setBold(true);
            Font grand = wb.createFont();
            grand.setBold(true);
            grand.setFontHeightInPoints((short) 14);
            Font blanc = wb.createFont();
            blanc.setBold(true);
            blanc.setColor(IndexedColors.WHITE.getIndex());
            Font gris = wb.createFont();
            gris.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

            titre = wb.createCellStyle();
            titre.setFont(grand);
            entete = wb.createCellStyle();
            entete.setFont(blanc);
            entete.setFillForegroundColor(IndexedColors.SEA_GREEN.getIndex());
            entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            entete.setWrapText(true);
            entete.setVerticalAlignment(VerticalAlignment.CENTER);
            normal = wb.createCellStyle();
            normal.setVerticalAlignment(VerticalAlignment.TOP);
            nombre = wb.createCellStyle();
            nombre.setAlignment(HorizontalAlignment.RIGHT);
            discret = wb.createCellStyle();
            discret.setFont(gris);
        }
    }

    private Sheet tableau(Workbook wb, Styles st, String nom, String[] entetes, List<Object[]> lignes) {
        Sheet sh = wb.createSheet(nom);
        Row h = sh.createRow(0);
        h.setHeightInPoints(30);
        for (int i = 0; i < entetes.length; i++) ecrire(h, i, entetes[i], st.entete);
        int l = 1;
        for (Object[] ligne : lignes) {
            Row r = sh.createRow(l++);
            for (int i = 0; i < ligne.length; i++) {
                Object v = ligne[i];
                ecrire(r, i, v == null ? "" : v, v instanceof Number ? st.nombre : st.normal);
            }
        }
        sh.createFreezePane(0, 1);                                  // en-tête figé au défilement
        if (l > 1) sh.setAutoFilter(new CellRangeAddress(0, l - 1, 0, entetes.length - 1));
        for (int i = 0; i < entetes.length; i++) {
            sh.setColumnWidth(i, Math.max(12, Math.min(40, entetes[i].length() + 4)) * 256);
        }
        return sh;
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> lignes(Map<String, Object> s, String cle, String... champs) {
        List<Object[]> res = new ArrayList<>();
        for (Map<String, Object> m : (List<Map<String, Object>>) s.get(cle)) {
            Object[] l = new Object[champs.length];
            for (int i = 0; i < champs.length; i++) l[i] = m.get(champs[i]);
            res.add(l);
        }
        return res;
    }

    private static void ecrire(Row r, int col, Object v, CellStyle style) {
        Cell c = r.createCell(col);
        if (v instanceof Number n) c.setCellValue(n.doubleValue());
        else c.setCellValue(v == null ? "" : String.valueOf(v));
        c.setCellStyle(style);
    }

    // ── Outils ─────────────────────────────────────────────────────────────

    private String moughataaDe(Reclamation r) {
        return r.getLocalisation() != null
                ? Moughataas.de(r.getLocalisation().getLatitude(), r.getLocalisation().getLongitude())
                : Moughataas.INCONNUE;
    }

    private static boolean aTraiter(Reclamation r) {
        return r.getStatut() == StatutReclamation.OUVERTE || r.getStatut() == StatutReclamation.EN_COURS;
    }

    private static boolean contientArabe(String s) {
        return s != null && s.codePoints().anyMatch(c -> c >= 0x0600 && c <= 0x06FF);
    }

    private static String libelle(Priorite p) {
        if (p == null) return "—";
        return switch (p) {
            case BASSE -> "Basse";
            case MOYENNE -> "Moyenne";
            case HAUTE -> "Haute";
            case CRITIQUE -> "Critique";
        };
    }

    private static String majuscule(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String nomComplet(Utilisateur u) {
        return (Objects.toString(u.getPrenom(), "") + " " + Objects.toString(u.getNom(), "")).trim();
    }

    private static Double moyenne(List<Double> valeurs) {
        return valeurs.isEmpty() ? null : arrondi(valeurs.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private static Double arrondi(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static Double pct(long n, long total) {
        return total == 0 ? 0.0 : arrondi(100.0 * n / total);
    }

    /** Petite fabrique de ligne ordonnée : ligne("cle1", v1, "cle2", v2, …). */
    private static Map<String, Object> ligne(Object... clesValeurs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < clesValeurs.length; i += 2) m.put((String) clesValeurs[i], clesValeurs[i + 1]);
        return m;
    }
}
