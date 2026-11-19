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
import com.fieldflow.workflow.SelectionRecue;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** T048 : POST /api/interventions/{id}/selection — codes 202 et 409 (contracts/api-rest.md). */
@AutoConfigureMockMvc
class SelectionIT extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    InterventionRepository interventions;

    @Autowired
    PropositionRepository propositions;

    /** Jean a accepté, Jacques a refusé, Pierre n'a pas répondu ; consultation close. */
    private UUID interventionEnAttenteDeSelection() {
        UUID id = UUID.randomUUID();
        Instant maintenant = Instant.now();
        interventions.insererSiAbsente(id, "Chaudière en panne", "12 rue des Lilas", maintenant.minusSeconds(60), maintenant);
        for (String t : new String[] {"Jean", "Jacques", "Pierre"}) {
            propositions.insererSiAbsente(id, t);
        }
        propositions.mettreAJourSiEnAttente(id, "Jean", Issue.ACCEPTEE.name(), null, null, maintenant);
        propositions.mettreAJourSiEnAttente(id, "Jacques", Issue.REFUSEE.name(), null, null, maintenant);
        propositions.mettreAJourSiEnAttente(id, "Pierre", Issue.SANS_REPONSE.name(), null, null, null);
        interventions.changerEtat(id, "EN_CONSULTATION", "EN_ATTENTE_SELECTION");
        return id;
    }

    private ResultActions selectionner(UUID id, String technicien) throws Exception {
        return mvc.perform(post("/api/interventions/{id}/selection", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"technicien\":\"" + technicien + "\"}"));
    }

    @Test
    void selectionDUnePropositionAccepteeTransmiseAuWorkflow() throws Exception {
        UUID id = interventionEnAttenteDeSelection();

        selectionner(id, "Jean").andExpect(status().isAccepted());

        ArgumentCaptor<Object> evenement = ArgumentCaptor.forClass(Object.class);
        verify(workflowClient).raiseEvent(eq(id.toString()), eq("selection"), evenement.capture());
        SelectionRecue s = (SelectionRecue) evenement.getValue();
        assertThat(s.technicien()).isEqualTo("Jean");
        assertThat(s.recuA()).isNotNull();
    }

    @Test
    void selectionDUnePropositionRefuseeOuSansReponseDonne409() throws Exception {
        UUID id = interventionEnAttenteDeSelection();

        selectionner(id, "Jacques").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELECTION_IMPOSSIBLE"));
        selectionner(id, "Pierre").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELECTION_IMPOSSIBLE"));

        verify(workflowClient, never()).raiseEvent(anyString(), anyString(), any());
    }

    @Test
    void secondeSelectionDonne409() throws Exception {
        UUID id = interventionEnAttenteDeSelection();
        interventions.selectionner(id, "Jean");

        selectionner(id, "Jean").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELECTION_IMPOSSIBLE"));
    }

    @Test
    void selectionPendantLaConsultationDonne409() throws Exception {
        UUID id = UUID.randomUUID();
        interventions.insererSiAbsente(id, "Chaudière", "12 rue des Lilas", Instant.now(), Instant.now().plusSeconds(60));
        propositions.insererSiAbsente(id, "Jean");
        propositions.mettreAJourSiEnAttente(id, "Jean", Issue.ACCEPTEE.name(), null, null, Instant.now());

        selectionner(id, "Jean").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELECTION_IMPOSSIBLE"));
    }
}
