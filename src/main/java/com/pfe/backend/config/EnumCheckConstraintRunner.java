package com.pfe.backend.config;

import com.pfe.backend.entity.enums.Priorite;
import com.pfe.backend.entity.enums.StatutReclamation;
import com.pfe.backend.entity.enums.TypeFeedbackIA;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Remet à jour, au démarrage, les contraintes CHECK des colonnes « énumération ».
 *
 * Pourquoi : Hibernate (ddl-auto=update) crée une contrainte CHECK avec la liste des valeurs
 * de l'enum au moment de la création de la table, mais ne la met JAMAIS à jour ensuite.
 * Ajouter une valeur Java (ex. FERMEE, ou les nouvelles décisions IA) faisait donc échouer
 * l'insertion (incident de l'étape 7). Solution provisoire jusqu'à Flyway (étape 9), où ces
 * contraintes seront écrites dans les migrations versionnées.
 *
 * Idempotent : on supprime puis recrée la contrainte avec TOUTES les valeurs actuelles de l'enum.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnumCheckConstraintRunner implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        synchroniser("reclamation", "statut", StatutReclamation.class);
        synchroniser("reclamation", "priorite", Priorite.class);
        synchroniser("reclamation", "prioriteia", Priorite.class);   // colonne priorite_ia (voir nomColonne)
        synchroniser("historique_statut", "ancien_statut", StatutReclamation.class);
        synchroniser("historique_statut", "nouveau_statut", StatutReclamation.class);
        synchroniser("feedbackia", "type_decision", TypeFeedbackIA.class);
    }

    private void synchroniser(String table, String colonne, Class<? extends Enum<?>> type) {
        String t = nomTable(table);
        String c = nomColonne(t, colonne);
        if (t == null || c == null) {
            log.debug("[EnumCheck] {}.{} absente, ignorée.", table, colonne);
            return;
        }
        String contrainte = t + "_" + c + "_check";
        String valeurs = Arrays.stream(type.getEnumConstants())
                .map(e -> "'" + e.name() + "'")
                .collect(Collectors.joining(","));
        try {
            jdbc.execute("ALTER TABLE " + t + " DROP CONSTRAINT IF EXISTS " + contrainte);
            jdbc.execute("ALTER TABLE " + t + " ADD CONSTRAINT " + contrainte
                    + " CHECK (" + c + " IN (" + valeurs + "))");
            log.info("[EnumCheck] {} = {}", contrainte, valeurs);
        } catch (Exception e) {
            // Ex. une ligne contient déjà une valeur inconnue : on n'empêche pas le démarrage.
            log.warn("[EnumCheck] {} non mise à jour : {}", contrainte, e.getMessage());
        }
    }

    /** La table de FeedbackIA peut s'appeler feedbackia ou feedback_ia selon la stratégie de nommage. */
    private String nomTable(String table) {
        for (String candidat : new String[]{table, table.replace("feedbackia", "feedback_ia")}) {
            Integer n = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = ?",
                    Integer.class, candidat);
            if (n != null && n > 0) return candidat;
        }
        return null;
    }

    /** prioriteIA → priorite_ia (nommage Spring) ; on vérifie que la colonne existe. */
    private String nomColonne(String table, String colonne) {
        if (table == null) return null;
        for (String candidat : new String[]{colonne, colonne.replace("prioriteia", "priorite_ia")}) {
            Integer n = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema = current_schema() "
                            + "AND table_name = ? AND column_name = ?",
                    Integer.class, table, candidat);
            if (n != null && n > 0) return candidat;
        }
        return null;
    }
}
