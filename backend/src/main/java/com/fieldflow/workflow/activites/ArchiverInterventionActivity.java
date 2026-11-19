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

/** Archive l'intervention (« Finir », R10) : elle n'est plus courante. Idempotente. */
@Component
@ActivityMetadata(name = "ArchiverIntervention")
public class ArchiverInterventionActivity implements WorkflowActivity {

    public record Entree(UUID interventionId, Instant a) {
    }

    private final InterventionRepository interventions;
    private final EvenementHistoriqueRepository historique;
    private final TransactionTemplate transaction;
    private final Notificateur notificateur;

    public ArchiverInterventionActivity(InterventionRepository interventions, EvenementHistoriqueRepository historique,
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
            LigneHistorique ligne = InterventionRules.ligneFin();
            historique.insererSiAbsent(e.interventionId(), ligne.cle(), e.a(), ligne.description());
            interventions.archiver(e.interventionId(), e.a());
        });
        notificateur.notifier(e.interventionId(), "FIN");
        return null;
    }
}
