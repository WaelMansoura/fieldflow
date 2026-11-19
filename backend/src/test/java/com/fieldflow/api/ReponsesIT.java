package com.fieldflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fieldflow.PostgresIntegrationTest;
import com.fieldflow.persistance.InterventionRepository;
import com.fieldflow.persistance.PropositionRepository;
import com.fieldflow.regles.Issue;
import com.fieldflow.regles.TypeReponse;
import com.fieldflow.workflow.ReponseRecue;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** T047 : POST /api/interventions/{id}/reponses — codes 202, 400, 404, 409 (contracts/api-rest.md). */
@AutoConfigureMockMvc
class ReponsesIT extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    InterventionRepository interventions;

    @Autowired
    PropositionRepository propositions;

    private UUID interventionEnConsultation(Instant echeance) {
        UUID id = UUID.randomUUID();
        interventions.insererSiAbsente(id, "Chaudière en panne", "12 rue des Lilas", Instant.now(), echeance);
        for (String t : new String[] {"Jean", "Jacques", "Pierre"}) {
            propositions.insererSiAbsente(id, t);
        }
        return id;
    }

    private ResultActions repondre(UUID id, String corps) throws Exception {
        return mvc.perform(post("/api/interventions/{id}/reponses", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps));
    }

    @Test
    void reponseAccepteeTransmiseAuWorkflow() throws Exception {
        UUID id = interventionEnConsultation(Instant.now().plusSeconds(60));

        repondre(id, """
                {"technicien":"Jacques","type":"ACCEPTER_AVEC_FRAIS","montant":40.00,"motif":"Déplacement"}
                """).andExpect(status().isAccepted());

        ArgumentCaptor<Object> evenement = ArgumentCaptor.forClass(Object.class);
        verify(workflowClient).raiseEvent(eq(id.toString()), eq("reponse"), evenement.capture());
        ReponseRecue r = (ReponseRecue) evenement.getValue();
        assertThat(r.technicien()).isEqualTo("Jacques");
        assertThat(r.type()).isEqualTo(TypeReponse.ACCEPTER_AVEC_FRAIS);
        assertThat(r.montant()).isEqualByComparingTo("40.00");
        assertThat(r.motif()).isEqualTo("Déplacement");
        assertThat(r.recuA()).isNotNull();
    }

    @Test
    void fraisInvalidesDonnent400() throws Exception {
        UUID id = interventionEnConsultation(Instant.now().plusSeconds(60));

        repondre(id, """
                {"technicien":"Jacques","type":"ACCEPTER_AVEC_FRAIS","montant":0,"motif":"Déplacement"}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FRAIS_INVALIDES"));

        verify(workflowClient, never()).raiseEvent(anyString(), anyString(), any());
    }

    @Test
    void typeAbsentDonne400() throws Exception {
        UUID id = interventionEnConsultation(Instant.now().plusSeconds(60));

        repondre(id, """
                {"technicien":"Jean"}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHAMP_INVALIDE"));
    }

    @Test
    void reponseApresLEcheanceDonne409() throws Exception {
        UUID id = interventionEnConsultation(Instant.now().minusSeconds(1));

        repondre(id, """
                {"technicien":"Pierre","type":"ACCEPTER"}
                """).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONSULTATION_CLOSE"));

        verify(workflowClient, never()).raiseEvent(anyString(), anyString(), any());
    }

    @Test
    void reponseApresLaClotureDonne409() throws Exception {
        UUID id = interventionEnConsultation(Instant.now().plusSeconds(60));
        interventions.changerEtat(id, "EN_CONSULTATION", "EN_ATTENTE_SELECTION");

        repondre(id, """
                {"technicien":"Pierre","type":"ACCEPTER"}
                """).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONSULTATION_CLOSE"));
    }

    @Test
    void secondeReponseDonne409() throws Exception {
        UUID id = interventionEnConsultation(Instant.now().plusSeconds(60));
        propositions.mettreAJourSiEnAttente(id, "Jean", Issue.ACCEPTEE.name(), null, null, Instant.now());

        repondre(id, """
                {"technicien":"Jean","type":"REFUSER"}
                """).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEJA_REPONDU"));
    }

    @Test
    void interventionOuTechnicienInconnuDonne404() throws Exception {
        UUID id = interventionEnConsultation(Instant.now().plusSeconds(60));

        repondre(UUID.randomUUID(), """
                {"technicien":"Jean","type":"ACCEPTER"}
                """).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INTERVENTION_INCONNUE"));
        repondre(id, """
                {"technicien":"Paul","type":"ACCEPTER"}
                """).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TECHNICIEN_INCONNU"));
    }
}
