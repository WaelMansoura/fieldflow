package com.fieldflow.workflow.activites;

import com.fieldflow.persistance.EvenementHistoriqueRepository;
import com.fieldflow.persistance.InterventionRepository;
import com.fieldflow.persistance.PropositionRepository;
import com.fieldflow.regles.InterventionRules;
import com.fieldflow.regles.LigneHistorique;
import io.dapr.spring.workflows.config.annotations.ActivityMetadata;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Crée l'intervention, ses propositions en attente (R1) et la ligne CREATION, en une transaction.
 * Idempotente. Renvoie false si une autre intervention est déjà active (index intervention_active).
 */
@Component
@ActivityMetadata(name = "CreerIntervention")
public class CreerInterventionActivity implements WorkflowActivity {

    public record Entree(UUID interventionId, String titre, String adresse, List<String> techniciens,
                         Instant creeA, Instant echeance) {
    }

    private final InterventionRepository interventions;
    private final PropositionRepository propositions;
    private final EvenementHistoriqueRepository historique;
    private final TransactionTemplate transaction;
    private final Notificateur notificateur;

    public CreerInterventionActivity(InterventionRepository interventions, PropositionRepository propositions,
                                     EvenementHistoriqueRepository historique, TransactionTemplate transaction,
                                     Notificateur notificateur) {
        this.interventions = interventions;
        this.propositions = propositions;
        this.historique = historique;
        this.transaction = transaction;
        this.notificateur = notificateur;
    }

    @Override
    public Object run(WorkflowActivityContext ctx) {
        Entree e = ctx.getInput(Entree.class);
        Boolean creee = transaction.execute(statut -> {
            interventions.insererSiAbsente(e.interventionId(), e.titre(), e.adresse(), e.creeA(), e.echeance());
            if (!interventions.existsById(e.interventionId())) {
                return false;
            }
            InterventionRules.propositionsInitiales(e.techniciens())
                    .keySet()
                    .forEach(t -> propositions.insererSiAbsente(e.interventionId(), t));
            LigneHistorique ligne = InterventionRules.ligneCreation(e.titre(), e.adresse());
            historique.insererSiAbsent(e.interventionId(), ligne.cle(), e.creeA(), ligne.description());
            return true;
        });
        if (Boolean.TRUE.equals(creee)) {
            notificateur.notifier(e.interventionId(), "CREATION");
        }
        return creee;
    }
}
