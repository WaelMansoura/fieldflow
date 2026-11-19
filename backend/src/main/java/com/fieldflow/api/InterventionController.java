package com.fieldflow.api;

import com.fieldflow.api.dto.CreationRequete;
import com.fieldflow.api.dto.EtapeRequete;
import com.fieldflow.api.dto.EvenementDto;
import com.fieldflow.api.dto.InterventionCouranteDto;
import com.fieldflow.api.dto.PropositionDto;
import com.fieldflow.api.dto.ReponseRequete;
import com.fieldflow.api.dto.SelectionRequete;
import com.fieldflow.persistance.EvenementHistoriqueRepository;
import com.fieldflow.persistance.Intervention;
import com.fieldflow.persistance.InterventionRepository;
import com.fieldflow.persistance.Proposition;
import com.fieldflow.persistance.PropositionRepository;
import com.fieldflow.persistance.Technicien;
import com.fieldflow.persistance.TechnicienRepository;
import com.fieldflow.regles.Decision;
import com.fieldflow.regles.EtatIntervention;
import com.fieldflow.regles.InterventionRules;
import com.fieldflow.workflow.EntreeWorkflow;
import com.fieldflow.workflow.EtapeRecue;
import com.fieldflow.workflow.FinRecue;
import com.fieldflow.workflow.InterventionWorkflow;
import com.fieldflow.workflow.ReponseRecue;
import com.fieldflow.workflow.SelectionRecue;
import io.dapr.workflows.client.DaprWorkflowClient;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API du chef et des réponses (contracts/api-rest.md). Le contrôleur lit la base, applique
 * InterventionRules (400/404/409) puis envoie l'événement au workflow et répond 202.
 * Il n'écrit jamais en base : seules les activités du workflow écrivent.
 */
@RestController
@RequestMapping("/api/interventions")
public class InterventionController {

    private final InterventionRepository interventions;
    private final PropositionRepository propositions;
    private final EvenementHistoriqueRepository historique;
    private final TechnicienRepository techniciens;
    private final DaprWorkflowClient workflowClient;
    private final Duration delai;

    public InterventionController(InterventionRepository interventions, PropositionRepository propositions,
                                  EvenementHistoriqueRepository historique, TechnicienRepository techniciens,
                                  DaprWorkflowClient workflowClient,
                                  @Value("${fieldflow.consultation.delai}") Duration delai) {
        this.interventions = interventions;
        this.propositions = propositions;
        this.historique = historique;
        this.techniciens = techniciens;
        this.workflowClient = workflowClient;
        this.delai = delai;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> creer(@Valid @RequestBody CreationRequete requete) {
        RegleRefuseeException.verifier(InterventionRules.validerCreation(requete.titre(), requete.adresse()));
        RegleRefuseeException.verifier(InterventionRules.peutCreer(interventions.trouverCourante().isPresent()));
        UUID id = UUID.randomUUID();
        List<String> noms = techniciens.findAllByOrderByNomAsc().stream().map(Technicien::getNom).toList();
        workflowClient.scheduleNewWorkflow(InterventionWorkflow.class,
                new EntreeWorkflow(id, requete.titre(), requete.adresse(), noms, delai.toSeconds()), id.toString());
        return ResponseEntity.accepted().body(Map.of("id", id));
    }

    @GetMapping("/courante")
    public ResponseEntity<InterventionCouranteDto> courante() {
        return interventions.trouverCourante()
                .map(i -> ResponseEntity.ok(versDto(i)))
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/reponses")
    public ResponseEntity<Void> repondre(@PathVariable UUID id, @Valid @RequestBody ReponseRequete requete) {
        Intervention intervention = trouver(id);
        Proposition proposition = trouverProposition(id, requete.technicien());
        Instant maintenant = Instant.now();
        RegleRefuseeException.verifier(InterventionRules.validerReponse(requete.type(), requete.montant(), requete.motif()));
        RegleRefuseeException.verifier(InterventionRules.peutRepondre(intervention.getEtat(), proposition.getIssue(),
                maintenant, intervention.getEcheance()));
        workflowClient.raiseEvent(id.toString(), "reponse",
                new ReponseRecue(requete.technicien(), requete.type(), requete.montant(), requete.motif(), maintenant));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/selection")
    public ResponseEntity<Void> selectionner(@PathVariable UUID id, @Valid @RequestBody SelectionRequete requete) {
        Intervention intervention = trouver(id);
        Proposition proposition = trouverProposition(id, requete.technicien());
        RegleRefuseeException.verifier(InterventionRules.peutSelectionner(intervention.getEtat(), proposition.getIssue()));
        workflowClient.raiseEvent(id.toString(), "selection", new SelectionRecue(requete.technicien(), Instant.now()));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/etapes")
    public ResponseEntity<Void> avancer(@PathVariable UUID id, @Valid @RequestBody EtapeRequete requete) {
        Intervention intervention = trouver(id);
        trouverProposition(id, requete.technicien());
        RegleRefuseeException.verifier(InterventionRules.peutPasserEtape(intervention.getEtat(), requete.etape(),
                requete.technicien(), intervention.getTechnicienSelectionne(), requete.rapport()));
        workflowClient.raiseEvent(id.toString(), "etape",
                new EtapeRecue(requete.technicien(), requete.etape(), requete.rapport(), Instant.now()));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/finir")
    public ResponseEntity<Void> finir(@PathVariable UUID id) {
        Intervention intervention = trouver(id);
        RegleRefuseeException.verifier(InterventionRules.peutFinir(intervention.getEtat()));
        workflowClient.raiseEvent(id.toString(), "finir", new FinRecue(Instant.now()));
        return ResponseEntity.accepted().build();
    }

    private InterventionCouranteDto versDto(Intervention i) {
        List<PropositionDto> props = propositions.findByInterventionIdOrderByTechnicienAsc(i.getId()).stream()
                .map(p -> new PropositionDto(p.getTechnicien(), p.getIssue(), p.getMontantFrais(), p.getMotifFrais(),
                        InterventionRules.peutSelectionner(i.getEtat(), p.getIssue()).estOk()))
                .toList();
        List<EvenementDto> evenements = historique.findByInterventionIdOrderBySurvenuAAscIdAsc(i.getId()).stream()
                .map(e -> new EvenementDto(e.getSurvenuA(), e.getDescription()))
                .toList();
        return new InterventionCouranteDto(i.getId(), i.getTitre(), i.getAdresse(), i.getEtat(), i.getEtat().libelle(),
                secondesRestantes(i), props, evenements, InterventionRules.peutFinir(i.getEtat()).estOk());
    }

    static long secondesRestantes(Intervention i) {
        if (i.getEtat() != EtatIntervention.EN_CONSULTATION) {
            return 0;
        }
        long ms = Duration.between(Instant.now(), i.getEcheance()).toMillis();
        return ms <= 0 ? 0 : (ms + 999) / 1000;
    }

    private Intervention trouver(UUID id) {
        return interventions.findById(id).orElseThrow(() -> new RegleRefuseeException(
                Decision.inconnu("INTERVENTION_INCONNUE", "Intervention inconnue.")));
    }

    private Proposition trouverProposition(UUID id, String technicien) {
        return propositions.findByInterventionIdAndTechnicien(id, technicien).orElseThrow(() -> new RegleRefuseeException(
                Decision.inconnu("TECHNICIEN_INCONNU", "Technicien inconnu : " + technicien)));
    }
}
