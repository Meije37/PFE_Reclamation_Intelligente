package com.pfe.backend.repository;

import com.pfe.backend.entity.Reclamation;
import com.pfe.backend.entity.enums.Priorite;
import com.pfe.backend.entity.enums.StatutReclamation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReclamationRepository extends JpaRepository<Reclamation, Long> {

    // Statistiques publiques (accueil visiteur)
    long countByStatut(StatutReclamation statut);

    // Human-in-the-Loop : réclamations signalées comme doublon potentiel par l'IA
    List<Reclamation> findByEstDoublonTrueOrderByDateCreationDesc();

    // Doublons confirmés rattachés à une réclamation d'origine
    List<Reclamation> findByDoublonDe_Id(Long origineId);

    /** Réclamations dont le périmètre municipal doit être vérifié par un humain. */
    List<Reclamation> findByPerimetreAVerifierTrueOrderByDateCreationDesc();

    /** Réclamations dont la catégorie choisie par le citoyen ne correspond pas au texte, selon l'IA. */
    List<Reclamation> findByCategorieAVerifierTrueOrderByDateCreationDesc();
    List<Reclamation> findByCitoyenId(Long id);
    // Variante paginee, utilisee par l'app mobile pour "Mes réclamations"
    // (évite de tout charger d'un coup si un citoyen en a beaucoup).
    Page<Reclamation> findByCitoyenIdOrderByDateCreationDesc(Long id, Pageable pageable);
    List<Reclamation> findByStatut(StatutReclamation statut);
    List<Reclamation> findByPriorite(Priorite priorite);
    @Query("SELECT COUNT(r) FROM Reclamation r WHERE r.citoyen.id = :id AND r.statut = :statut")
    long countByCitoyenAndStatut(@Param("id") Long id, @Param("statut") StatutReclamation statut);

    // Tu peux aussi ajouter celle-ci pour le total
    long countByCitoyenId(Long id);
    @Query("""
        SELECT COUNT(a) FROM Affectation a
        WHERE a.agent.id = :agentId
        AND a.reclamation.statut = :statut
    """)
    Long countAffectationsParStatut(
            @Param("agentId") Long agentId,
            @Param("statut") StatutReclamation statut
    );

    // ── Liste publique "Réclamations publiques" (toutes, tous citoyens) ──
    // Filtres optionnels : passer null pour ignorer un critère.

    @Query("""
        SELECT r FROM Reclamation r
        WHERE (:zoneId IS NULL OR r.categorie.serviceResponsable.zone.id = :zoneId)
          AND (:categorieId IS NULL OR r.categorie.idCategorie = :categorieId)
          AND (:statut IS NULL OR r.statut = :statut)
        ORDER BY r.dateCreation DESC
    """)
    Page<Reclamation> findPubliques(
            @Param("zoneId") Long zoneId,
            @Param("categorieId") Long categorieId,
            @Param("statut") StatutReclamation statut,
            Pageable pageable);

    // Variante triée par nombre de votes (les plus soutenues d'abord).
    // countQuery explicite : la requête principale a un GROUP BY, Spring ne
    // peut pas en déduire automatiquement une requête de comptage fiable.
    @Query(
            value = """
            SELECT r FROM Reclamation r
            LEFT JOIN Vote v ON v.reclamation = r
            WHERE (:zoneId IS NULL OR r.categorie.serviceResponsable.zone.id = :zoneId)
              AND (:categorieId IS NULL OR r.categorie.idCategorie = :categorieId)
              AND (:statut IS NULL OR r.statut = :statut)
            GROUP BY r
            ORDER BY COUNT(v) DESC, r.dateCreation DESC
        """,
            countQuery = """
            SELECT COUNT(r) FROM Reclamation r
            WHERE (:zoneId IS NULL OR r.categorie.serviceResponsable.zone.id = :zoneId)
              AND (:categorieId IS NULL OR r.categorie.idCategorie = :categorieId)
              AND (:statut IS NULL OR r.statut = :statut)
        """
    )
    Page<Reclamation> findPubliquesTrieesParVotes(
            @Param("zoneId") Long zoneId,
            @Param("categorieId") Long categorieId,
            @Param("statut") StatutReclamation statut,
            Pageable pageable);

    /**
     * Candidats à la détection de doublons (IA, étape 6) : réclamations encore ouvertes ou en cours,
     * déposées depuis `depuis`, dans un carré autour du point (filtre grossier ; le service IA
     * calcule ensuite la vraie distance et la similarité du texte).
     */
    @Query("""
        SELECT r FROM Reclamation r
        WHERE r.statut IN :statuts
          AND r.dateCreation >= :depuis
          AND r.localisation.latitude BETWEEN :latMin AND :latMax
          AND r.localisation.longitude BETWEEN :lonMin AND :lonMax
        ORDER BY r.dateCreation DESC
    """)
    List<Reclamation> findCandidatsDoublon(
            @Param("statuts") List<StatutReclamation> statuts,
            @Param("depuis") java.time.LocalDateTime depuis,
            @Param("latMin") Double latMin, @Param("latMax") Double latMax,
            @Param("lonMin") Double lonMin, @Param("lonMax") Double lonMax,
            Pageable pageable);

}