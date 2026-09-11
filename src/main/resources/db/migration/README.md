# Migrations Flyway

## Contexte
Ce projet utilisait `spring.jpa.hibernate.ddl-auto=update` pour gérer le
schéma de la base de données. À partir de maintenant, en **production**
uniquement, Flyway prend le relais :

- `ddl-auto=validate` : Hibernate vérifie juste que le schéma correspond aux
  entités, il ne le modifie plus jamais tout seul.
- Flyway est configuré en `baseline-on-migrate=true` avec
  `baseline-version=1` : au premier démarrage sur la base existante, il
  marque l'état actuel comme "V1" (sans exécuter aucun SQL dessus, la base
  n'est pas touchée). Les migrations que tu ajoutes ici doivent donc
  commencer à `V2`.

En **développement** (profil `dev`, celui que tu utilises tous les jours),
rien ne change : `spring.flyway.enabled=false` et Hibernate continue de
gérer le schéma automatiquement avec `ddl-auto=update`, comme avant.

## Comment ajouter une évolution de schéma (uniquement utile pour la prod)

1. Modifie tes entités JPA comme d'habitude.
2. Démarre l'app en local (profil dev) : Hibernate met à jour ta base de
   dev automatiquement, comme avant.
3. Regarde les logs Hibernate (`spring.jpa.show-sql=true`) ou utilise un
   outil de diff de schéma pour identifier le SQL exact généré
   (`ALTER TABLE ...`, `ADD COLUMN ...`, etc.).
4. Crée un fichier ici nommé `V2__description_courte.sql` (puis `V3`, `V4`,
   en incrémentant à chaque nouvelle migration, jamais en réutilisant un
   numéro) contenant ce SQL.
5. Committe ce fichier avec le code qui en dépend. Au prochain déploiement
   en prod, Flyway l'appliquera automatiquement, dans l'ordre des numéros.

## Première mise en place sur le serveur de prod (une seule fois)

Avant de déployer cette version pour la première fois sur ta base de prod
existante :

1. Fais un **backup** de la base de prod (`pg_dump`), par précaution.
2. Déploie normalement avec `SPRING_PROFILES_ACTIVE=prod`. Flyway va créer
   sa table `flyway_schema_history` et la marquer directement à la version
   1, sans toucher au reste du schéma.
3. Vérifie au démarrage qu'il n'y a aucune erreur Flyway/Hibernate dans les
   logs (`validate` échoue si le schéma réel ne correspond pas exactement
   aux entités : dans ce cas, corrige le schéma à la main avant de relancer).

Aucun fichier `V1__xxx.sql` n'est nécessaire ni présent dans ce dossier :
le "V1" correspond à l'état actuel de la base, pas à un script exécuté.