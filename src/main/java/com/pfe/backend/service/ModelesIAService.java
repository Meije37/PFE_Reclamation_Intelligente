package com.pfe.backend.service;

import com.pfe.backend.entity.FeedbackIA;
import com.pfe.backend.entity.Reclamation;
import com.pfe.backend.repository.FeedbackIARepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Réentraînement versionné des modèles IA à partir des décisions des administrateurs
 * (Human-in-the-Loop, point 4).
 *
 *  1. Les décisions humaines (périmètre, priorité) sont lues dans feedback_ia ;
 *  2. elles sont envoyées au service Python (POST /models/retrain), qui entraîne une NOUVELLE
 *     version, la mesure contre la version active et la garde de côté SANS l'activer ;
 *  3. l'administrateur compare puis active la version (POST /models/activate), ou revient
 *     à une version précédente à tout moment.
 *
 * Client HTTP dédié : un réentraînement prend quelques secondes à une minute, bien plus que
 * le délai de 5 s utilisé pour analyser une réclamation (ServiceIAClient).
 */
@Service
public class ModelesIAService {

    private static final Logger log = LoggerFactory.getLogger(ModelesIAService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final FeedbackIARepository feedbackRepository;
    private final RestTemplate http;

    @Value("${ia.service.url}")
    private String iaServiceUrl;

    public ModelesIAService(FeedbackIARepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(180_000);   // 3 minutes au plus pour un réentraînement
        this.http = new RestTemplate(factory);
    }

    /** Erreur à afficher telle quelle à l'administrateur. */
    public static class ErreurServiceIA extends RuntimeException {
        private final int statut;
        public ErreurServiceIA(int statut, String message) { super(message); this.statut = statut; }
        public int getStatut() { return statut; }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> versions() {
        return appeler(() -> http.getForObject(iaServiceUrl + "/models/versions", Map.class));
    }

    /** Envoie toutes les décisions humaines et renvoie la nouvelle version (non activée) avec sa comparaison. */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public Map<String, Object> reentrainer(String emailAdmin) {
        List<Map<String, Object>> perimetre = new ArrayList<>();
        for (FeedbackIA f : feedbackRepository.findByTypeDecisionInOrderByDateDecisionAsc(
                SupervisionIAService.DECISIONS_PERIMETRE)) {
            String texte = texteDe(f.getReclamation());
            if (texte == null || f.getValeurHumaine() == null) continue;
            perimetre.add(Map.of("texte", texte,
                    "is_municipal", SupervisionIAService.MUNICIPAL.equals(f.getValeurHumaine()) ? 1 : 0));
        }
        List<Map<String, Object>> priorite = new ArrayList<>();
        for (FeedbackIA f : feedbackRepository.findByTypeDecisionInOrderByDateDecisionAsc(
                SupervisionIAService.DECISIONS_PRIORITE)) {
            String texte = texteDe(f.getReclamation());
            if (texte == null || f.getValeurHumaine() == null) continue;
            priorite.add(Map.of("texte", texte, "priorite", f.getValeurHumaine()));
        }
        if (perimetre.isEmpty() && priorite.isEmpty()) {
            throw new ErreurServiceIA(400, "Aucune décision humaine pour l'instant : confirmez ou corrigez d'abord "
                    + "le périmètre ou la priorité de réclamations dans « Vérifications IA ».");
        }
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("decisions_perimetre", perimetre);
        corps.put("decisions_priorite", priorite);
        corps.put("auteur", emailAdmin);
        log.info("[IA] Réentraînement demandé par {} : {} décisions périmètre, {} décisions priorité",
                emailAdmin, perimetre.size(), priorite.size());
        return appeler(() -> http.postForObject(iaServiceUrl + "/models/retrain", corps, Map.class));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> activer(String version, String emailAdmin) {
        Map<String, Object> r = appeler(() -> http.postForObject(
                iaServiceUrl + "/models/activate", Map.of("version", version), Map.class));
        log.info("[IA] Version {} des modèles activée par {}", version, emailAdmin);
        return r;
    }

    // ── Outils ─────────────────────────────────────────────────────────────

    private interface Appel { Map<String, Object> executer(); }

    private Map<String, Object> appeler(Appel appel) {
        try {
            Map<String, Object> r = appel.executer();
            if (r == null) throw new ErreurServiceIA(502, "Réponse vide du service IA.");
            return r;
        } catch (HttpStatusCodeException e) {
            // FastAPI renvoie {"detail": "..."} : on transmet ce message à l'administrateur.
            String message = "Le service IA a refusé la demande.";
            try {
                Object detail = JSON.readValue(e.getResponseBodyAsString(), Map.class).get("detail");
                if (detail instanceof String s && !s.isBlank()) message = s;
            } catch (Exception ignore) { }
            throw new ErreurServiceIA(e.getStatusCode().value() == 400 ? 400 : 502, message);
        } catch (ResourceAccessException e) {
            throw new ErreurServiceIA(503, "Service IA injoignable : lancez-le (python app_ai.py) puis réessayez.");
        }
    }

    private static String texteDe(Reclamation r) {
        if (r == null) return null;
        String t = (r.getTitre() == null ? "" : r.getTitre() + ". ") + Objects.toString(r.getDescription(), "");
        return t.isBlank() ? null : t;
    }
}
