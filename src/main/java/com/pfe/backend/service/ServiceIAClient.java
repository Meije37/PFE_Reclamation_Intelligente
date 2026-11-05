package com.pfe.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client du microservice IA (pfe-ai-service, FastAPI).
 *
 * Règle d'or : l'IA AIDE, elle ne BLOQUE jamais. Si le service Python est
 * arrêté, lent ou renvoie une réponse inattendue, chaque méthode renvoie
 * Optional.empty() et la réclamation est enregistrée quand même (avec la
 * logique de secours de ReclamationService).
 *
 * Les réponses sont lues comme des Map (même style que le reste du projet),
 * pour ne pas dépendre de la version de Jackson.
 */
@Component
public class ServiceIAClient {

    private static final Logger log = LoggerFactory.getLogger(ServiceIAClient.class);

    private final RestTemplate restTemplate;

    @Value("${ia.service.url}")
    private String iaServiceUrl;

    public ServiceIAClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ── Résultats ──────────────────────────────────────────────────────────

    /** Réponse de POST /analyze. */
    public record ResultatAnalyse(
            boolean estMunicipal,
            double probabiliteMunicipal,
            boolean decisionAutomatique,   // faux = un agent doit confirmer « municipal / hors périmètre »
            String priorite,               // BASSE, MOYENNE, HAUTE, CRITIQUE
            boolean alerteCritique,        // priorité relevée par la règle « sécurité d'abord »
            double scoreUrgence,
            String incident,               // type d'incident reconnu (peut être null)
            double confiancePriorite,      // < 0,5 : l'IA hésite sur la priorité
            String familleSuggeree,        // ex. EAU_ASSAINISSEMENT, déduite de l'incident (peut être null)
            double confianceFamille,       // probabilité de l'incident qui a donné cette famille
            Map<String, Object> brut       // réponse complète, enregistrée dans AnalyseIA
    ) {}

    /** Un voisin renvoyé par POST /similar. */
    public record Voisin(long id, double distanceM, double similarite, boolean doublonPotentiel) {}

    /** Réclamation candidate envoyée à POST /similar. */
    public record Candidat(long id, String texte, double latitude, double longitude) {}

    // ── Appels ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Optional<ResultatAnalyse> analyser(String texte) {
        String url = iaServiceUrl + "/analyze";
        try {
            Map<String, Object> r = restTemplate.postForObject(url, Map.of("texte", texte), Map.class);
            if (r == null || r.get("priorite") == null) {
                log.warn("Réponse IA inattendue sur {} : {}", url, r);
                return Optional.empty();
            }
            Map<String, Object> incident = (Map<String, Object>) r.get("incident");
            Map<String, Object> suggestion = (Map<String, Object>) r.get("categorie_suggeree");
            return Optional.of(new ResultatAnalyse(
                    Boolean.TRUE.equals(r.get("is_municipal")),
                    nombre(r.get("probabilite_municipal"), 0.5),
                    Boolean.TRUE.equals(r.get("decision_automatique")),
                    String.valueOf(r.get("priorite")),
                    Boolean.TRUE.equals(r.get("alerte_critique")),
                    nombre(r.get("score_urgence"), 0.5),
                    incident != null ? String.valueOf(incident.get("code")) : null,
                    nombre(r.get("confiance_priorite"), 1.0),
                    suggestion != null ? String.valueOf(suggestion.get("famille")) : null,
                    suggestion != null ? nombre(suggestion.get("probabilite"), 0) : 0,
                    r));
        } catch (Exception e) {
            log.warn("Microservice IA injoignable ({}) : {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    public Optional<List<Voisin>> similaires(String texte, double latitude, double longitude,
                                             List<Candidat> candidats) {
        if (candidats.isEmpty()) {
            return Optional.of(List.of());
        }
        String url = iaServiceUrl + "/similar";
        try {
            List<Map<String, Object>> corpsCandidats = new ArrayList<>();
            for (Candidat c : candidats) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", c.id());
                m.put("texte", c.texte());
                m.put("latitude", c.latitude());
                m.put("longitude", c.longitude());
                corpsCandidats.add(m);
            }
            Map<String, Object> corps = new LinkedHashMap<>();
            corps.put("texte", texte);
            corps.put("latitude", latitude);
            corps.put("longitude", longitude);
            corps.put("candidats", corpsCandidats);

            Map<String, Object> r = restTemplate.postForObject(url, corps, Map.class);
            if (r == null || !(r.get("voisins") instanceof List<?>)) {
                log.warn("Réponse IA inattendue sur {} : {}", url, r);
                return Optional.empty();
            }
            List<Voisin> voisins = new ArrayList<>();
            for (Object o : (List<Object>) r.get("voisins")) {
                Map<String, Object> v = (Map<String, Object>) o;
                voisins.add(new Voisin(
                        ((Number) v.get("id")).longValue(),
                        nombre(v.get("distance_m"), 0),
                        nombre(v.get("similarite"), 0),
                        Boolean.TRUE.equals(v.get("doublon_potentiel"))));
            }
            return Optional.of(voisins);
        } catch (Exception e) {
            log.warn("Détection de doublons indisponible ({}) : {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    /** Le JSON peut renvoyer 1 ou 1.0 : on passe par Number plutôt qu'un cast direct en Double. */
    private static double nombre(Object o, double defaut) {
        return o instanceof Number n ? n.doubleValue() : defaut;
    }
}
