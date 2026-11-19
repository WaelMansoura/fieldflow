package com.fieldflow.persistance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EvenementHistoriqueRepository extends JpaRepository<EvenementHistorique, Long> {

    /** Un événement de même clé n'est jamais enregistré deux fois (FR-021, FR-031). */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO evenement_historique (intervention_id, cle, survenu_a, description)
            VALUES (:interventionId, :cle, :survenuA, :description)
            ON CONFLICT (intervention_id, cle) DO NOTHING
            """, nativeQuery = true)
    int insererSiAbsent(@Param("interventionId") UUID interventionId, @Param("cle") String cle,
                        @Param("survenuA") Instant survenuA, @Param("description") String description);

    List<EvenementHistorique> findByInterventionIdOrderBySurvenuAAscIdAsc(UUID interventionId);
}
