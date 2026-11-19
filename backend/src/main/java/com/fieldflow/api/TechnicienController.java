package com.fieldflow.api;

import com.fieldflow.api.dto.VueTechnicienDto;
import com.fieldflow.persistance.Intervention;
import com.fieldflow.persistance.InterventionRepository;
import com.fieldflow.persistance.Proposition;
import com.fieldflow.persistance.PropositionRepository;
import com.fieldflow.persistance.Technicien;
import com.fieldflow.persistance.TechnicienRepository;
import com.fieldflow.regles.Decision;
import com.fieldflow.regles.InterventionRules;
import com.fieldflow.regles.Issue;
import com.fieldflow.regles.VueTechnicien;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/techniciens")
public class TechnicienController {

    private final TechnicienRepository techniciens;
    private final InterventionRepository interventions;
    private final PropositionRepository propositions;

    public TechnicienController(TechnicienRepository techniciens, InterventionRepository interventions,
                                PropositionRepository propositions) {
        this.techniciens = techniciens;
        this.interventions = interventions;
        this.propositions = propositions;
    }

    @GetMapping
    public List<String> lister() {
        return techniciens.findAllByOrderByNomAsc().stream().map(Technicien::getNom).toList();
    }

    /** Ce que voit le technicien (R3, R7) : calculé par InterventionRules.vueTechnicien. */
    @GetMapping("/{nom}/vue")
    public VueTechnicienDto vue(@PathVariable String nom) {
        if (!techniciens.existsById(nom)) {
            throw new RegleRefuseeException(Decision.inconnu("TECHNICIEN_INCONNU", "Technicien inconnu : " + nom));
        }
        Optional<Intervention> courante = interventions.trouverCourante();
        if (courante.isEmpty()) {
            return VueTechnicienDto.vide();
        }
        Intervention i = courante.get();
        Issue issue = propositions.findByInterventionIdAndTechnicien(i.getId(), nom).map(Proposition::getIssue).orElse(null);
        boolean selectionne = nom.equals(i.getTechnicienSelectionne());
        VueTechnicien vue = InterventionRules.vueTechnicien(i.getEtat(), issue, selectionne);
        return switch (vue) {
            case VIDE -> VueTechnicienDto.vide();
            case CONSULTATION -> new VueTechnicienDto(vue, i.getId(), i.getTitre(), i.getAdresse(),
                    InterventionController.secondesRestantes(i), null, null, null);
            case ATTENTE -> new VueTechnicienDto(vue, i.getId(), i.getTitre(), i.getAdresse(), null, issue, null, null);
            case ETAPES -> new VueTechnicienDto(vue, i.getId(), i.getTitre(), i.getAdresse(), null, null, i.getEtat(),
                    InterventionRules.etapeSuivante(i.getEtat()));
        };
    }
}
