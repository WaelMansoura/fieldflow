package com.fieldflow.workflow;

import com.fieldflow.regles.EtatIntervention;
import com.fieldflow.regles.InterventionRules;
import com.fieldflow.regles.Issue;
import com.fieldflow.regles.ResultatCloture;
import com.fieldflow.workflow.activites.ArchiverInterventionActivity;
import com.fieldflow.workflow.activites.CloreConsultationActivity;
import com.fieldflow.workflow.activites.CreerInterventionActivity;
import com.fieldflow.workflow.activites.EnregistrerEtapeActivity;
import com.fieldflow.workflow.activites.EnregistrerReponseActivity;
import com.fieldflow.workflow.activites.SelectionnerTechnicienActivity;
import io.dapr.durabletask.Task;
import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowStub;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dapr.workflows.WorkflowTaskRetryPolicy;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Orchestration d'une intervention (contracts/workflow.md). Code déterministe (principe II) :
 * aucun accès à la base, aucun appel réseau, aucun Instant.now() ni hasard. Les heures viennent de
 * ctx.getCurrentInstant() ou des événements ; tout effet passe par une activité idempotente.
 * Les règles viennent de InterventionRules ; un événement refusé par les règles est ignoré.
 */
@Component
public class InterventionWorkflow implements Workflow {

    /** Reprise des activités : délai croissant de 1 s à 30 s, 20 tentatives au maximum (research R-05). */
    private static final WorkflowTaskOptions REPRISE = new WorkflowTaskOptions(WorkflowTaskRetryPolicy.newBuilder()
            .setMaxNumberOfAttempts(20)
            .setFirstRetryInterval(Duration.ofSeconds(1))
            .setBackoffCoefficient(2.0)
            .setMaxRetryInterval(Duration.ofSeconds(30))
            .build());

    @Override
    public WorkflowStub create() {
        return this::executer;
    }

    private void executer(WorkflowContext ctx) {
        EntreeWorkflow entree = ctx.getInput(EntreeWorkflow.class);
        UUID id = entree.interventionId();

        // 1-2. Échéance (R2) puis création ; si une autre intervention est active, on s'arrête.
        Instant creeA = ctx.getCurrentInstant();
        Instant echeance = InterventionRules.echeance(creeA, Duration.ofSeconds(entree.delaiSecondes()));
        Boolean creee = ctx.callActivity("CreerIntervention",
                new CreerInterventionActivity.Entree(id, entree.titre(), entree.adresse(), entree.techniciens(),
                        creeA, echeance),
                REPRISE, Boolean.class).await();
        if (!Boolean.TRUE.equals(creee)) {
            ctx.complete(null);
            return;
        }

        // 3. Consultation : un seul timer durable, une réponse à la fois (R1, R3, R4, R5).
        Map<String, Issue> issues = InterventionRules.propositionsInitiales(entree.techniciens());
        Task<Void> timer = ctx.createTimer(ZonedDateTime.ofInstant(echeance, ZoneOffset.UTC));
        Instant closA = echeance;
        while (!InterventionRules.consultationComplete(issues.values())) {
            Task<ReponseRecue> attente = ctx.waitForExternalEvent("reponse", ReponseRecue.class);
            Task<?> gagnant = ctx.anyOf(timer, attente).await();
            if (gagnant == timer) {
                closA = echeance;
                break;
            }
            ReponseRecue r = attente.await();
            if (reponseAcceptable(r, issues, echeance)) {
                Issue issue = InterventionRules.issuePour(r.type());
                issues.put(r.technicien(), issue);
                ctx.callActivity("EnregistrerReponse",
                        new EnregistrerReponseActivity.Entree(id, r.technicien(), issue, r.montant(), r.motif(), r.recuA()),
                        REPRISE).await();
                closA = r.recuA();
            }
        }

        // 4. Clôture.
        ResultatCloture cloture = InterventionRules.clore(issues);
        cloture.sansReponse().forEach(t -> issues.put(t, Issue.SANS_REPONSE));
        ctx.callActivity("CloreConsultation", new CloreConsultationActivity.Entree(id, cloture, closA), REPRISE).await();

        if (cloture.etatFinal() == EtatIntervention.REFUSEE) {
            attendreFinir(ctx, id);
            return;
        }

        // 5. Sélection (R6) : les sélections invalides sont ignorées.
        String selectionne = null;
        while (selectionne == null) {
            SelectionRecue s = ctx.waitForExternalEvent("selection", SelectionRecue.class).await();
            if (InterventionRules.peutSelectionner(EtatIntervention.EN_ATTENTE_SELECTION, issues.get(s.technicien())).estOk()) {
                ctx.callActivity("SelectionnerTechnicien",
                        new SelectionnerTechnicienActivity.Entree(id, s.technicien(), s.recuA()), REPRISE).await();
                selectionne = s.technicien();
            }
        }

        // 6. Étapes (R8) : En route → Sur site → Terminée ; les étapes invalides sont ignorées.
        EtatIntervention etat = EtatIntervention.SELECTIONNEE;
        while (etat != EtatIntervention.TERMINEE) {
            EtapeRecue e = ctx.waitForExternalEvent("etape", EtapeRecue.class).await();
            if (InterventionRules.peutPasserEtape(etat, e.etape(), e.technicien(), selectionne, e.rapport()).estOk()) {
                ctx.callActivity("EnregistrerEtape",
                        new EnregistrerEtapeActivity.Entree(id, e.etape(), etat, e.technicien(), e.rapport(), e.recuA()),
                        REPRISE).await();
                etat = InterventionRules.etatApres(e.etape());
            }
        }

        // 7. Finir (R10).
        attendreFinir(ctx, id);
    }

    /** Terminée ou Refusée : attend « Finir » du chef, archive l'intervention, puis termine le workflow. */
    private static void attendreFinir(WorkflowContext ctx, UUID id) {
        FinRecue fin = ctx.waitForExternalEvent("finir", FinRecue.class).await();
        ctx.callActivity("ArchiverIntervention", new ArchiverInterventionActivity.Entree(id, fin.recuA()), REPRISE).await();
        ctx.complete(null);
    }

    private static boolean reponseAcceptable(ReponseRecue r, Map<String, Issue> issues, Instant echeance) {
        if (r == null || !issues.containsKey(r.technicien()) || r.recuA() == null) {
            return false;
        }
        return InterventionRules.validerReponse(r.type(), r.montant(), r.motif()).estOk()
                && InterventionRules.peutRepondre(EtatIntervention.EN_CONSULTATION, issues.get(r.technicien()),
                        r.recuA(), echeance).estOk();
    }
}
