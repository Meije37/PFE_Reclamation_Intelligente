package com.pfe.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0) // avant les autres runners (création de l'admin, données de démonstration)
@RequiredArgsConstructor
@Slf4j
public class SchemaFixRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    private static final String CONSTRAINT_NAME = "fksd11g74lsohtrmjkigx0mb7bc";

    @Override
    public void run(String... args) {
        // Hibernate (ddl-auto=update) n'agrandit pas une colonne existante : message passe en TEXT ici.
        try {
            jdbcTemplate.execute("ALTER TABLE notification ALTER COLUMN message TYPE TEXT");
        } catch (Exception e) {
            log.warn("[SchemaFix] Colonne notification.message : {}", e.getMessage());
        }

        try {
            String tableReferencee = jdbcTemplate.queryForObject("""
                SELECT ccu.table_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.constraint_column_usage ccu
                  ON tc.constraint_name = ccu.constraint_name
                WHERE tc.table_name = 'intervention'
                  AND tc.constraint_type = 'FOREIGN KEY'
                  AND tc.constraint_name = ?
                """, String.class, CONSTRAINT_NAME);

            if ("service".equals(tableReferencee)) {
                log.warn("[SchemaFix] Correction de la contrainte en cours...");
                jdbcTemplate.execute("ALTER TABLE intervention DROP CONSTRAINT " + CONSTRAINT_NAME);
                jdbcTemplate.execute(
                        "ALTER TABLE intervention ADD CONSTRAINT fk_intervention_service " +
                                "FOREIGN KEY (service_id) REFERENCES services(id)"
                );
                log.info("[SchemaFix] Contrainte corrigée avec succès.");
            }
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            log.debug("[SchemaFix] Contrainte introuvable, ignoré.");
        } catch (Exception e) {
            log.warn("[SchemaFix] Erreur ignorée : {}", e.getMessage());
        }
    }
}