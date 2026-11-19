package com.fieldflow.persistance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PropositionRepository extends JpaRepository<Proposition, Long> {

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO proposition (intervention_id, technicien, issue)
            VALUES (:interventionId, :technicien, 'EN_ATTENTE')
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insererSiAbsente(@Param("interventionId") UUID interventionId, @Param("technicien") String technicien);

    /** Ne modifie qu'une proposition encore en attente : une réponse déjà enregistrée ne change jamais. */
    @Transactional
    @Modifying
    @Query(value = """
            UPDATE proposition
            SET issue = :issue, montant_frais = :montant, motif_frais = :motif, repondu_a = :reponduA
            WHERE intervention_id = :interventionId AND technicien = :technicien AND issue = 'EN_ATTENTE'
            """, nativeQuery = true)
    int mettreAJourSiEnAttente(@Param("interventionId") UUID interventionId, @Param("technicien") String technicien,
                               @Param("issue") String issue, @Param("montant") BigDecimal montant,
                               @Param("motif") String motif, @Param("reponduA") Instant reponduA);

    List<Proposition> findByInterventionIdOrderByTechnicienAsc(UUID interventionId);

    Optional<Proposition> findByInterventionIdAndTechnicien(UUID interventionId, String technicien);
}
