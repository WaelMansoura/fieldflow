package com.fieldflow.workflow.activites;

import com.fieldflow.persistance.EvenementHistoriqueRepository;
import com.fieldflow.persistance.InterventionRepository;
import com.fieldflow.regles.InterventionRules;
import com.fieldflow.regles.LigneHistorique;
import io.dapr.spring.workflows.config.annotations.ActivityMetadata;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Enregistre la sélection du chef (R6) et sa ligne d'historique, en une transaction. Idempotente. */
@Component
@ActivityMetadata(name = "SelectionnerTechnicien")
public class SelectionnerTechnicienActivity implements WorkflowActivity {

    public record Entree(UUID interventionId, String technicien, Instant recuA) {
    }

    private final InterventionRepository interventions;
    private final EvenementHistoriqueRepository historique;
    private final TransactionTemplate transaction;
    private final Notificateur notificateur;

    public SelectionnerTechnicienActivity(InterventionRepository interventions, EvenementHistoriqueRepository historique,
                                          TransactionTemplate transaction, Notificateur notificateur) {
        this.interventions = interventions;
        this.historique = historique;
        this.transaction = transaction;
        this.notificateur = notificateur;
    }

    @Override
    public Object run(WorkflowActivityContext ctx) {
        Entree e = ctx.getInput(Entree.class);
        transaction.executeWithoutResult(statut -> {
            interventions.selectionner(e.interventionId(), e.technicien());
            LigneHistorique ligne = InterventionRules.ligneSelection(e.technicien());
            historique.insererSiAbsent(e.interventionId(), ligne.cle(), e.recuA(), ligne.description());
        });
        notificateur.notifier(e.interventionId(), "SELECTION");
        return null;
    }
}
