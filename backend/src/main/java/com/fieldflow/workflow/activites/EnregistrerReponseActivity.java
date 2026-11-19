package com.fieldflow.workflow.activites;

import com.fieldflow.persistance.EvenementHistoriqueRepository;
import com.fieldflow.persistance.PropositionRepository;
import com.fieldflow.regles.InterventionRules;
import com.fieldflow.regles.Issue;
import com.fieldflow.regles.LigneHistorique;
import io.dapr.spring.workflows.config.annotations.ActivityMetadata;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Enregistre la réponse d'un technicien et sa ligne d'historique, en une transaction. Idempotente. */
@Component
@ActivityMetadata(name = "EnregistrerReponse")
public class EnregistrerReponseActivity implements WorkflowActivity {

    public record Entree(UUID interventionId, String technicien, Issue issue, BigDecimal montant, String motif,
                         Instant recuA) {
    }

    private final PropositionRepository propositions;
    private final EvenementHistoriqueRepository historique;
    private final TransactionTemplate transaction;
    private final Notificateur notificateur;

    public EnregistrerReponseActivity(PropositionRepository propositions, EvenementHistoriqueRepository historique,
                                      TransactionTemplate transaction, Notificateur notificateur) {
        this.propositions = propositions;
        this.historique = historique;
        this.transaction = transaction;
        this.notificateur = notificateur;
    }

    @Override
    public Object run(WorkflowActivityContext ctx) {
        Entree e = ctx.getInput(Entree.class);
        transaction.executeWithoutResult(statut -> {
            int modifiees = propositions.mettreAJourSiEnAttente(e.interventionId(), e.technicien(), e.issue().name(),
                    e.montant(), e.motif(), e.recuA());
            // Si la proposition n'était plus en attente, c'est une ré-exécution : la ligne existe déjà.
            if (modifiees == 1) {
                LigneHistorique ligne = InterventionRules.ligneReponse(e.technicien(), e.issue(), e.montant(), e.motif());
                historique.insererSiAbsent(e.interventionId(), ligne.cle(), e.recuA(), ligne.description());
            }
        });
        notificateur.notifier(e.interventionId(), "REPONSE");
        return null;
    }
}
