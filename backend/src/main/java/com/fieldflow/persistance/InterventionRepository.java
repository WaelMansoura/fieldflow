package com.fieldflow.persistance;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Écritures idempotentes (research R-05) : insertion ON CONFLICT DO NOTHING et mises à jour
 * conditionnelles sur l'état attendu. Chaque méthode renvoie le nombre de lignes modifiées.
 */
public interface InterventionRepository extends JpaRepository<Intervention, UUID> {

    @Query("select i from Intervention i where i.archiveeA is null")
    Optional<Intervention> trouverCourante();

    /** Ne fait rien si l'id existe déjà ou si une autre intervention est active (intervention_active). */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO intervention (id, titre, adresse, etat, cree_a, echeance)
            VALUES (:id, :titre, :adresse, 'EN_CONSULTATION', :creeA, :echeance)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insererSiAbsente(@Param("id") UUID id, @Param("titre") String titre, @Param("adresse") String adresse,
                         @Param("creeA") Instant creeA, @Param("echeance") Instant echeance);

    @Transactional
    @Modifying
    @Query(value = "UPDATE intervention SET etat = :nouveau WHERE id = :id AND etat = :attendu",
            nativeQuery = true)
    int changerEtat(@Param("id") UUID id, @Param("attendu") String attendu, @Param("nouveau") String nouveau);

    @Transactional
    @Modifying
    @Query(value = """
            UPDATE intervention SET etat = 'SELECTIONNEE', technicien_selectionne = :technicien
            WHERE id = :id AND etat = 'EN_ATTENTE_SELECTION'
            """, nativeQuery = true)
    int selectionner(@Param("id") UUID id, @Param("technicien") String technicien);

    @Transactional
    @Modifying
    @Query(value = "UPDATE intervention SET etat = 'TERMINEE', rapport = :rapport WHERE id = :id AND etat = 'SUR_SITE'",
            nativeQuery = true)
    int terminer(@Param("id") UUID id, @Param("rapport") String rapport);

    @Transactional
    @Modifying
    @Query(value = "UPDATE intervention SET archivee_a = :a WHERE id = :id AND archivee_a IS NULL",
            nativeQuery = true)
    int archiver(@Param("id") UUID id, @Param("a") Instant a);
}
