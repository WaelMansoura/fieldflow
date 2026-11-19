package com.fieldflow.workflow.activites;

import com.fieldflow.persistance.EvenementHistoriqueRepository;
import com.fieldflow.persistance.InterventionRepository;
import com.fieldflow.persistance.PropositionRepository;
import com.fieldflow.regles.EtatIntervention;
import com.fieldflow.regles.InterventionRules;
import com.fieldflow.regles.Issue;
import com.fieldflow.regles.LigneHistorique;
import com.fieldflow.regles.ResultatCloture;
import io.dapr.spring.workflows.config.annotations.ActivityMetadata;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Clôt la consultation (R3) : sans-réponse, nouvel état et lignes d'historique, en une transaction. Idempotente. */
@Component
@ActivityMetadata(name = "CloreConsultation")
public class CloreConsultationActivity implements WorkflowActivity {

    public record Entree(UUID interventionId, ResultatCloture resultat, Instant closA) {
    }

    private final InterventionRepository interventions;
    private final PropositionRepository propositions;
    private final EvenementHistoriqueRepository historique;
    private final TransactionTemplate transaction;
    private final Notificateur notificateur;

    public CloreConsultationActivity(InterventionRepository interventions, PropositionRepository propositions,
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
        UUID id = e.interventionId();
        transaction.executeWithoutResult(statut -> {
            for (String technicien : e.resultat().sansReponse()) {
                propositions.mettreAJourSiEnAttente(id, technicien, Issue.SANS_REPONSE.name(), null, null, null);
                LigneHistorique ligne = InterventionRules.ligneSansReponse(technicien);
                historique.insererSiAbsent(id, ligne.cle(), e.closA(), ligne.description());
            }
            interventions.changerEtat(id, EtatIntervention.EN_CONSULTATION.name(), e.resultat().etatFinal().name());
            LigneHistorique ligne = InterventionRules.ligneCloture(e.resultat());
            historique.insererSiAbsent(id, ligne.cle(), e.closA(), ligne.description());
        });
        notificateur.notifier(id, "CLOTURE");
        return null;
    }
}
