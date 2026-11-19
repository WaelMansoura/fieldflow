package com.fieldflow.persistance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fieldflow.PostgresIntegrationTest;
import com.fieldflow.regles.Issue;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T016 : idempotence des écritures (research R-05, FR-031) sur une vraie base. */
class PersistanceIT extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-19T09:00:00Z");

    @Autowired
    InterventionRepository interventions;

    @Autowired
    PropositionRepository propositions;

    @Autowired
    EvenementHistoriqueRepository historique;

    @Test
    void insererDeuxFoisLaMemeInterventionNeCreeQuUneLigne() {
        UUID id = UUID.randomUUID();

        assertThat(interventions.insererSiAbsente(id, "Chaudière", "12 rue des Lilas", T0, T0.plusSeconds(60))).isEqualTo(1);
        assertThat(interventions.insererSiAbsente(id, "Chaudière", "12 rue des Lilas", T0, T0.plusSeconds(60))).isEqualTo(0);

        assertThat(interventions.count()).isEqualTo(1);
    }

    @Test
    void uneSecondeInterventionActiveEstRefuseeParIntervention_active() {
        interventions.insererSiAbsente(UUID.randomUUID(), "Première", "A", T0, T0.plusSeconds(60));

        int inseree = interventions.insererSiAbsente(UUID.randomUUID(), "Seconde", "B", T0, T0.plusSeconds(60));

        assertThat(inseree).isZero();
        assertThat(interventions.count()).isEqualTo(1);
    }

    @Test
    void uneNouvelleInterventionEstPossibleApresArchivage() {
        UUID premiere = UUID.randomUUID();
        interventions.insererSiAbsente(premiere, "Première", "A", T0, T0.plusSeconds(60));
        interventions.archiver(premiere, T0.plusSeconds(120));

        int inseree = interventions.insererSiAbsente(UUID.randomUUID(), "Seconde", "B", T0, T0.plusSeconds(60));

        assertThat(inseree).isEqualTo(1);
    }

    @Test
    void insererDeuxFoisLaMemePropositionNeCreeQuUneLigne() {
        UUID id = interventionEnConsultation();

        propositions.insererSiAbsente(id, "Jean");
        propositions.insererSiAbsente(id, "Jean");

        assertThat(propositions.findByInterventionIdOrderByTechnicienAsc(id)).hasSize(1);
    }

    @Test
    void insererDeuxFoisLeMemeEvenementNeCreeQuUneLigne() {
        UUID id = interventionEnConsultation();

        historique.insererSiAbsent(id, "CREATION", T0, "Intervention créée");
        historique.insererSiAbsent(id, "CREATION", T0, "Intervention créée");

        assertThat(historique.findByInterventionIdOrderBySurvenuAAscIdAsc(id)).hasSize(1);
    }

    @Test
    void uneReponseDejaEnregistreeNEstJamaisModifiee() {
        UUID id = interventionEnConsultation();
        propositions.insererSiAbsente(id, "Jacques");
        propositions.mettreAJourSiEnAttente(id, "Jacques", Issue.ACCEPTEE_AVEC_FRAIS.name(),
                new BigDecimal("40.00"), "Déplacement", T0.plusSeconds(10));

        int modifiees = propositions.mettreAJourSiEnAttente(id, "Jacques", Issue.SANS_REPONSE.name(),
                null, null, null);

        Proposition jacques = propositions.findByInterventionIdAndTechnicien(id, "Jacques").orElseThrow();
        assertThat(modifiees).isZero();
        assertThat(jacques.getIssue()).isEqualTo(Issue.ACCEPTEE_AVEC_FRAIS);
        assertThat(jacques.getMontantFrais()).isEqualByComparingTo("40.00");
        assertThat(jacques.getMotifFrais()).isEqualTo("Déplacement");
    }

    @Test
    void unChangementDEtatNeSAppliqueQueDepuisLEtatAttendu() {
        UUID id = interventionEnConsultation();

        assertThat(interventions.changerEtat(id, "EN_CONSULTATION", "EN_ATTENTE_SELECTION")).isEqualTo(1);
        assertThat(interventions.changerEtat(id, "EN_CONSULTATION", "EN_ATTENTE_SELECTION")).isZero();
    }

    private UUID interventionEnConsultation() {
        UUID id = UUID.randomUUID();
        interventions.insererSiAbsente(id, "Chaudière", "12 rue des Lilas", T0, T0.plusSeconds(60));
        return id;
    }
}
