package com.fieldflow.workflow.activites;

import com.fieldflow.persistance.EvenementHistoriqueRepository;
import com.fieldflow.persistance.InterventionRepository;
import com.fieldflow.regles.Etape;
import com.fieldflow.regles.EtatIntervention;
import com.fieldflow.regles.InterventionRules;
import com.fieldflow.regles.LigneHistorique;
import io.dapr.spring.workflows.config.annotations.ActivityMetadata;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Enregistre une étape (R8) depuis l'état attendu, et sa ligne d'historique, en une transaction. Idempotente. */
@Component
@ActivityMetadata(name = "EnregistrerEtape")
public class EnregistrerEtapeActivity implements WorkflowActivity {

    public record Entree(UUID interventionId, Etape etape, EtatIntervention etatAttendu, String technicien,
                         String rapport, Instant recuA) {
    }

    private final InterventionRepository interventions;
    private final EvenementHistoriqueRepository historique;
    private final TransactionTemplate transaction;
    private final Notificateur notificateur;

    public EnregistrerEtapeActivity(InterventionRepository interventions, EvenementHistoriqueRepository historique,
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
            int modifiees = e.etape() == Etape.TERMINEE
                    ? interventions.terminer(e.interventionId(), e.rapport())
                    : interventions.changerEtat(e.interventionId(), e.etatAttendu().name(),
                            InterventionRules.etatApres(e.etape()).name());
            // Si l'état n'était plus l'état attendu, c'est une ré-exécution : la ligne existe déjà.
            if (modifiees == 1) {
                LigneHistorique ligne = InterventionRules.ligneEtape(e.etape(), e.technicien(), e.rapport());
                historique.insererSiAbsent(e.interventionId(), ligne.cle(), e.recuA(), ligne.description());
            }
        });
        notificateur.notifier(e.interventionId(), "ETAPE");
        return null;
    }
}
