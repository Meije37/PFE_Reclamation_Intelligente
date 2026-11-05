package com.pfe.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protection contre les essais en série (« force brute ») sur les points d'entrée sensibles.
 *
 *  - Connexion : 5 mots de passe faux pour un même e-mail en 15 min → e-mail bloqué 15 min.
 *                20 échecs depuis une même adresse IP (tous e-mails confondus) → IP bloquée 15 min
 *                (empêche d'essayer un mot de passe courant sur beaucoup de comptes).
 *  - Code de réinitialisation (6 chiffres) : 5 codes faux → bloqué 15 min
 *                (sinon on pourrait tester les 1 000 000 de codes possibles).
 *  - Demande de code (« mot de passe oublié ») : 3 demandes par e-mail et 10 par IP en 15 min
 *                (évite d'inonder une boîte mail).
 *
 * Fenêtre glissante : seuls les échecs des 15 dernières minutes comptent. Une connexion réussie
 * remet le compteur de l'e-mail à zéro. Les compteurs sont gardés en mémoire (suffisant pour un
 * seul serveur ; avec plusieurs serveurs on les mettrait dans Redis).
 */
@Slf4j
@Service
public class ProtectionConnexionService {

    private final int maxEchecsEmail;
    private final int maxEchecsIp;
    private final int maxEchecsCode;
    private final int maxDemandesCodeEmail;
    private final int maxDemandesCodeIp;
    private final Duration fenetre;
    private final Duration blocage;
    private final Clock horloge;

    private final Map<String, Compteur> compteurs = new ConcurrentHashMap<>();

    @Autowired
    public ProtectionConnexionService(
            @Value("${app.securite.connexion.max-echecs:5}") int maxEchecsEmail,
            @Value("${app.securite.connexion.max-echecs-ip:20}") int maxEchecsIp,
            @Value("${app.securite.code.max-echecs:5}") int maxEchecsCode,
            @Value("${app.securite.code.max-demandes:3}") int maxDemandesCodeEmail,
            @Value("${app.securite.code.max-demandes-ip:10}") int maxDemandesCodeIp,
            @Value("${app.securite.fenetre-minutes:15}") long fenetreMinutes,
            @Value("${app.securite.blocage-minutes:15}") long blocageMinutes) {
        this(maxEchecsEmail, maxEchecsIp, maxEchecsCode, maxDemandesCodeEmail, maxDemandesCodeIp,
                Duration.ofMinutes(fenetreMinutes), Duration.ofMinutes(blocageMinutes), Clock.systemUTC());
    }

    /** Constructeur des tests (horloge contrôlée). */
    ProtectionConnexionService(int maxEchecsEmail, int maxEchecsIp, int maxEchecsCode,
                               int maxDemandesCodeEmail, int maxDemandesCodeIp,
                               Duration fenetre, Duration blocage, Clock horloge) {
        this.maxEchecsEmail = maxEchecsEmail;
        this.maxEchecsIp = maxEchecsIp;
        this.maxEchecsCode = maxEchecsCode;
        this.maxDemandesCodeEmail = maxDemandesCodeEmail;
        this.maxDemandesCodeIp = maxDemandesCodeIp;
        this.fenetre = fenetre;
        this.blocage = blocage;
        this.horloge = horloge;
    }

    // ── Connexion ──────────────────────────────────────────────────────────

    /** Secondes d'attente si l'e-mail ou l'IP est bloqué, 0 sinon. */
    public long attenteConnexion(String email, String ip) {
        return Math.max(attente(cle("connexion", email)), attente(cle("connexion-ip", ip)));
    }

    /** Enregistre un mot de passe faux. Renvoie le nombre d'essais restants (0 = vient d'être bloqué). */
    public int echecConnexion(String email, String ip) {
        enregistrer(cle("connexion-ip", ip), maxEchecsIp);
        return enregistrer(cle("connexion", email), maxEchecsEmail);
    }

    public void succesConnexion(String email) {
        compteurs.remove(cle("connexion", email));
    }

    // ── Code de réinitialisation ───────────────────────────────────────────

    public long attenteCode(String email) {
        return attente(cle("code", email));
    }

    public int echecCode(String email) {
        return enregistrer(cle("code", email), maxEchecsCode);
    }

    public void succesCode(String email) {
        compteurs.remove(cle("code", email));
    }

    /**
     * Compte une demande de code. Renvoie les secondes d'attente si la limite est déjà atteinte
     * (la demande est alors refusée et n'est pas comptée), 0 si la demande est acceptée.
     */
    public long demandeCode(String email, String ip) {
        long deja = Math.max(attente(cle("demande", email)), attente(cle("demande-ip", ip)));
        if (deja > 0) return deja;
        enregistrer(cle("demande", email), maxDemandesCodeEmail);
        enregistrer(cle("demande-ip", ip), maxDemandesCodeIp);
        return 0;
    }

    // ── Mécanique commune ──────────────────────────────────────────────────

    private static final class Compteur {
        final Deque<Instant> evenements = new ArrayDeque<>();
        Instant bloqueJusqua;
    }

    private long attente(String cle) {
        Compteur c = compteurs.get(cle);
        if (c == null) return 0;
        synchronized (c) {
            Instant maintenant = horloge.instant();
            if (c.bloqueJusqua != null && maintenant.isBefore(c.bloqueJusqua)) {
                // arrondi au-dessus : jamais « 0 seconde » tant que c'est bloqué
                return Math.max(1, (Duration.between(maintenant, c.bloqueJusqua).toMillis() + 999) / 1000);
            }
            return 0;
        }
    }

    private int enregistrer(String cle, int maximum) {
        nettoyerSiBesoin();
        Compteur c = compteurs.computeIfAbsent(cle, k -> new Compteur());
        synchronized (c) {
            Instant maintenant = horloge.instant();
            if (c.bloqueJusqua != null && !maintenant.isBefore(c.bloqueJusqua)) {
                c.bloqueJusqua = null;          // blocage terminé : on repart de zéro
                c.evenements.clear();
            }
            while (!c.evenements.isEmpty() && c.evenements.peekFirst().isBefore(maintenant.minus(fenetre))) {
                c.evenements.pollFirst();       // hors de la fenêtre glissante
            }
            c.evenements.addLast(maintenant);
            int restants = maximum - c.evenements.size();
            if (restants <= 0) {
                c.bloqueJusqua = maintenant.plus(blocage);
                log.warn("[Sécurité] Blocage de {} pendant {} min après {} tentatives.",
                        cle, blocage.toMinutes(), c.evenements.size());
                return 0;
            }
            return restants;
        }
    }

    /** Évite que la mémoire grossisse indéfiniment (compteurs anciens supprimés). */
    private void nettoyerSiBesoin() {
        if (compteurs.size() < 10_000) return;
        Instant limite = horloge.instant().minus(fenetre.plus(blocage));
        compteurs.entrySet().removeIf(e -> {
            Compteur c = e.getValue();
            synchronized (c) {
                Instant dernier = c.evenements.peekLast();
                boolean bloque = c.bloqueJusqua != null && horloge.instant().isBefore(c.bloqueJusqua);
                return !bloque && (dernier == null || dernier.isBefore(limite));
            }
        });
    }

    private static String cle(String type, String valeur) {
        return type + ":" + (valeur == null ? "" : valeur.trim().toLowerCase(Locale.ROOT));
    }

    /** « 15 minutes », « 1 minute », « 40 secondes » — pour les messages. */
    public static String duree(long secondes) {
        if (secondes < 60) return secondes + (secondes > 1 ? " secondes" : " seconde");
        long minutes = (secondes + 59) / 60;
        return minutes + (minutes > 1 ? " minutes" : " minute");
    }
}
