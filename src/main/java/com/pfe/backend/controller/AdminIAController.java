package com.pfe.backend.controller;

import com.pfe.backend.service.DoublonIAService;
import com.pfe.backend.service.ModelesIAService;
import com.pfe.backend.service.SupervisionIAService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Supervision de l'IA par l'administrateur (Human-in-the-Loop).
 * Sécurité : /api/admin/** est réservé au rôle ADMIN dans SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/ia")
@RequiredArgsConstructor
public class AdminIAController {

    private final DoublonIAService doublonService;
    private final SupervisionIAService supervisionService;
    private final ModelesIAService modelesService;

    /** GET /api/admin/ia/doublons — doublons potentiels en attente de décision. */
    @GetMapping("/doublons")
    public List<Map<String, Object>> doublonsAVerifier() {
        return doublonService.aVerifier();
    }

    /** POST /api/admin/ia/doublons/{id}/confirmer  { "origineId": 12, "commentaire": "…" } */
    @PostMapping("/doublons/{id}/confirmer")
    public ResponseEntity<?> confirmer(@PathVariable Long id, @RequestBody Map<String, Object> corps,
                                       Authentication auth) {
        Object origine = corps.get("origineId");
        if (!(origine instanceof Number n)) {
            return ResponseEntity.badRequest().body(Map.of("message", "origineId est obligatoire."));
        }
        try {
            doublonService.confirmer(id, n.longValue(), auth.getName(), texte(corps.get("commentaire")));
            return ResponseEntity.ok(Map.of("message", "Doublon confirmé."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** POST /api/admin/ia/doublons/{id}/rejeter  { "commentaire": "…" } (corps facultatif) */
    @PostMapping("/doublons/{id}/rejeter")
    public ResponseEntity<?> rejeter(@PathVariable Long id,
                                     @RequestBody(required = false) Map<String, Object> corps,
                                     Authentication auth) {
        try {
            doublonService.rejeter(id, auth.getName(), corps != null ? texte(corps.get("commentaire")) : null);
            return ResponseEntity.ok(Map.of("message", "Proposition rejetée : ce n'est pas un doublon."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * GET /api/admin/ia/feedback/stats — l'IA face aux décisions humaines.
     * Clés historiques (aVerifier, confirmes, rejetes, precisionObservee) = doublons ;
     * « perimetre » et « priorite » = les deux autres modèles.
     */
    @GetMapping("/feedback/stats")
    public Map<String, Object> statistiques() {
        Map<String, Object> s = new java.util.LinkedHashMap<>(doublonService.statistiques());
        s.putAll(supervisionService.statistiques());
        return s;
    }

    // ── Périmètre municipal (modèle is_municipal) ─────────────────────────

    /** GET /api/admin/ia/perimetre — réclamations dont le périmètre est à vérifier. */
    @GetMapping("/perimetre")
    public List<Map<String, Object>> perimetreAVerifier() {
        return supervisionService.perimetreAVerifier();
    }

    /** POST /api/admin/ia/perimetre/{id}/municipal  { "commentaire": "…" } (corps facultatif) */
    @PostMapping("/perimetre/{id}/municipal")
    public ResponseEntity<?> confirmerMunicipal(@PathVariable Long id,
                                                @RequestBody(required = false) Map<String, Object> corps,
                                                Authentication auth) {
        try {
            supervisionService.confirmerMunicipal(id, auth.getName(),
                    corps != null ? texte(corps.get("commentaire")) : null);
            return ResponseEntity.ok(Map.of("message", "Réclamation confirmée comme municipale."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** POST /api/admin/ia/perimetre/{id}/hors-perimetre  { "motif": "Adressez-vous à la SOMELEC…" } */
    @PostMapping("/perimetre/{id}/hors-perimetre")
    public ResponseEntity<?> confirmerHorsPerimetre(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> corps,
                                                    Authentication auth) {
        try {
            supervisionService.confirmerHorsPerimetre(id, auth.getName(), texte(corps.get("motif")));
            return ResponseEntity.ok(Map.of("message", "Réclamation rejetée : hors du périmètre de la commune."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ── Catégorie (erreur de catégorie du citoyen) ────────────────────────

    /** GET /api/admin/ia/categorie — réclamations dont la catégorie semble erronée selon l'IA. */
    @GetMapping("/categorie")
    public List<Map<String, Object>> categorieAVerifier() {
        return supervisionService.categorieAVerifier();
    }

    /** PUT /api/admin/ia/categorie/{id}  { "categorieId": 3, "commentaire": "…" } — garder ou corriger. */
    @PutMapping("/categorie/{id}")
    public ResponseEntity<?> changerCategorie(@PathVariable Long id, @RequestBody Map<String, Object> corps,
                                              Authentication auth) {
        Long categorieId = corps.get("categorieId") instanceof Number n ? n.longValue() : null;
        try {
            return ResponseEntity.ok(supervisionService.changerCategorie(id, auth.getName(), categorieId,
                    texte(corps.get("commentaire"))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ── Priorité ──────────────────────────────────────────────────────────

    /** PUT /api/admin/ia/priorite/{id}  { "priorite": "CRITIQUE", "commentaire": "…" } */
    @PutMapping("/priorite/{id}")
    public ResponseEntity<?> validerPriorite(@PathVariable Long id, @RequestBody Map<String, Object> corps,
                                             Authentication auth) {
        try {
            return ResponseEntity.ok(supervisionService.validerPriorite(id, auth.getName(),
                    texte(corps.get("priorite")), texte(corps.get("commentaire"))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** GET /api/admin/ia/feedback/export?type=doublons|perimetre|priorite|categorie — dataset_v2 (CSV UTF-8 avec BOM). */
    @GetMapping("/feedback/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "doublons") String type) {
        String nom = switch (type) {
            case "perimetre" -> "perimetre";
            case "priorite" -> "priorite";
            case "categorie" -> "categorie";
            default -> "doublons";
        };
        String csv = switch (nom) {
            case "perimetre" -> supervisionService.exportPerimetreCsv();
            case "priorite" -> supervisionService.exportPrioriteCsv();
            case "categorie" -> supervisionService.exportCategorieCsv();
            default -> doublonService.exportCsv();
        };
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] contenu = csv.getBytes(StandardCharsets.UTF_8);
        byte[] fichier = new byte[bom.length + contenu.length];
        System.arraycopy(bom, 0, fichier, 0, bom.length);
        System.arraycopy(contenu, 0, fichier, bom.length, contenu.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"dataset_v2_" + nom + "_" + LocalDate.now() + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(fichier);
    }

    private static String texte(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : (s.length() > 500 ? s.substring(0, 500) : s);
    }

    // ── Versions des modèles : réentraînement à partir des décisions humaines ──

    /** GET /api/admin/ia/modeles — versions des modèles et version active. */
    @GetMapping("/modeles")
    public ResponseEntity<?> modeles() {
        return reponse(() -> modelesService.versions());
    }

    /** POST /api/admin/ia/modeles/reentrainer — nouvelle version (NON activée) + comparaison. */
    @PostMapping("/modeles/reentrainer")
    public ResponseEntity<?> reentrainer(Authentication auth) {
        return reponse(() -> modelesService.reentrainer(auth.getName()));
    }

    /** POST /api/admin/ia/modeles/v3/activer — active une version (sert aussi au retour arrière). */
    @PostMapping("/modeles/{version}/activer")
    public ResponseEntity<?> activer(@PathVariable String version, Authentication auth) {
        if (!version.matches("v\\d+")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Version invalide."));
        }
        return reponse(() -> modelesService.activer(version, auth.getName()));
    }

    private ResponseEntity<?> reponse(java.util.function.Supplier<Map<String, Object>> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (ModelesIAService.ErreurServiceIA e) {
            return ResponseEntity.status(e.getStatut()).body(Map.of("message", e.getMessage()));
        }
    }
}
