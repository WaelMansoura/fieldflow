package com.fieldflow.regles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Toutes les règles métier R1 à R10 (principe III). Java pur : aucune dépendance à Spring, Dapr ou JPA.
 * Le workflow, les activités et l'API ne font qu'appeler ces méthodes.
 */
public final class InterventionRules {

    static final int LONGUEUR_MAX = 200;

    private InterventionRules() {
    }

    // --- Création et R1 ---

    public static Decision validerCreation(String titre, String adresse) {
        if (!texteValide(titre) || !texteValide(adresse)) {
            return Decision.invalide("CHAMP_INVALIDE", "Le titre et l'adresse sont obligatoires (200 caractères au maximum).");
        }
        return Decision.ok();
    }

    public static Decision peutCreer(boolean interventionActiveExiste) {
        if (interventionActiveExiste) {
            return Decision.mauvaisEtat("INTERVENTION_ACTIVE", "Une intervention est déjà en cours.");
        }
        return Decision.ok();
    }

    /** R1 : à la création, chaque technicien reçoit l'intervention, sa proposition est en attente. */
    public static Map<String, Issue> propositionsInitiales(List<String> techniciens) {
        Map<String, Issue> issues = new LinkedHashMap<>();
        techniciens.forEach(t -> issues.put(t, Issue.EN_ATTENTE));
        return issues;
    }

    // --- R2 : délai ---

    public static Instant echeance(Instant debut, Duration delai) {
        return debut.plus(delai);
    }

    // --- R5 : réponse et frais ---

    public static Decision validerReponse(TypeReponse type, BigDecimal montant, String motif) {
        if (type == null) {
            return Decision.invalide("CHAMP_INVALIDE", "Le type de réponse est obligatoire.");
        }
        if (type != TypeReponse.ACCEPTER_AVEC_FRAIS) {
            return Decision.ok();
        }
        if (montant == null || montant.signum() <= 0 || montant.stripTrailingZeros().scale() > 2) {
            return Decision.invalide("FRAIS_INVALIDES", "Le montant doit être supérieur à 0 €, avec au plus 2 décimales.");
        }
        if (!texteValide(motif)) {
            return Decision.invalide("FRAIS_INVALIDES", "Le motif est obligatoire (200 caractères au maximum).");
        }
        return Decision.ok();
    }

    public static Issue issuePour(TypeReponse type) {
        return switch (type) {
            case ACCEPTER -> Issue.ACCEPTEE;
            case ACCEPTER_AVEC_FRAIS -> Issue.ACCEPTEE_AVEC_FRAIS;
            case REFUSER -> Issue.REFUSEE;
        };
    }

    // --- R4 : réponse tardive ou en double ---

    public static Decision peutRepondre(EtatIntervention etat, Issue issueActuelle, Instant maintenant, Instant echeance) {
        if (etat != EtatIntervention.EN_CONSULTATION || !maintenant.isBefore(echeance)) {
            return Decision.mauvaisEtat("CONSULTATION_CLOSE", "La consultation est close : réponse refusée.");
        }
        if (issueActuelle != Issue.EN_ATTENTE) {
            return Decision.mauvaisEtat("DEJA_REPONDU", "Vous avez déjà répondu à cette consultation.");
        }
        return Decision.ok();
    }

    // --- R3 : clôture ---

    public static boolean consultationComplete(Collection<Issue> issues) {
        return issues.stream().noneMatch(i -> i == Issue.EN_ATTENTE);
    }

    public static ResultatCloture clore(Map<String, Issue> issues) {
        List<String> sansReponse = issues.entrySet().stream()
                .filter(e -> e.getValue() == Issue.EN_ATTENTE)
                .map(Map.Entry::getKey)
                .toList();
        int acceptations = (int) issues.values().stream().filter(Issue::estAcceptee).count();
        EtatIntervention etat = acceptations > 0 ? EtatIntervention.EN_ATTENTE_SELECTION : EtatIntervention.REFUSEE;
        return new ResultatCloture(etat, sansReponse, acceptations);
    }

    // --- R6 : sélection unique ---

    public static Decision peutSelectionner(EtatIntervention etat, Issue issue) {
        if (etat != EtatIntervention.EN_ATTENTE_SELECTION || issue == null || !issue.estAcceptee()) {
            return Decision.mauvaisEtat("SELECTION_IMPOSSIBLE", "Seule une proposition acceptée peut être sélectionnée, une seule fois.");
        }
        return Decision.ok();
    }

    // --- R3 et R7 : ce que voit chaque technicien ---

    public static VueTechnicien vueTechnicien(EtatIntervention etat, Issue issue, boolean estSelectionne) {
        if (etat == null) {
            return VueTechnicien.VIDE;
        }
        return switch (etat) {
            case EN_CONSULTATION -> issue == Issue.EN_ATTENTE ? VueTechnicien.CONSULTATION : VueTechnicien.ATTENTE;
            case EN_ATTENTE_SELECTION -> issue != null && issue.estAcceptee() ? VueTechnicien.ATTENTE : VueTechnicien.VIDE;
            case SELECTIONNEE, EN_ROUTE, SUR_SITE -> estSelectionne ? VueTechnicien.ETAPES : VueTechnicien.VIDE;
            case TERMINEE, REFUSEE -> VueTechnicien.VIDE;
        };
    }

    // --- R8 : étapes ---

    /** Seule étape possible depuis l'état courant, ou null s'il n'y en a pas. */
    public static Etape etapeSuivante(EtatIntervention etat) {
        return switch (etat) {
            case SELECTIONNEE -> Etape.EN_ROUTE;
            case EN_ROUTE -> Etape.SUR_SITE;
            case SUR_SITE -> Etape.TERMINEE;
            default -> null;
        };
    }

    public static EtatIntervention etatApres(Etape etape) {
        return switch (etape) {
            case EN_ROUTE -> EtatIntervention.EN_ROUTE;
            case SUR_SITE -> EtatIntervention.SUR_SITE;
            case TERMINEE -> EtatIntervention.TERMINEE;
        };
    }

    public static Decision peutPasserEtape(EtatIntervention etat, Etape etape, String technicien,
                                           String technicienSelectionne, String rapport) {
        if (etape == null) {
            return Decision.invalide("CHAMP_INVALIDE", "L'étape est obligatoire.");
        }
        if (technicienSelectionne == null || !technicienSelectionne.equals(technicien)) {
            return Decision.mauvaisEtat("NON_SELECTIONNE", "Seul le technicien sélectionné peut faire avancer l'intervention.");
        }
        if (etape != etapeSuivante(etat)) {
            return Decision.mauvaisEtat("ETAPE_HORS_ORDRE", "Les étapes se suivent : En route, Sur site, puis Terminer.");
        }
        if (etape == Etape.TERMINEE && (rapport == null || rapport.isBlank())) {
            return Decision.invalide("RAPPORT_OBLIGATOIRE", "Le rapport est obligatoire pour terminer.");
        }
        return Decision.ok();
    }

    // --- R10 : Finir ---

    public static Decision peutFinir(EtatIntervention etat) {
        if (etat != EtatIntervention.TERMINEE && etat != EtatIntervention.REFUSEE) {
            return Decision.mauvaisEtat("FINIR_IMPOSSIBLE", "L'intervention doit être terminée ou refusée.");
        }
        return Decision.ok();
    }

    // --- R9 : lignes d'historique ---

    public static LigneHistorique ligneCreation(String titre, String adresse) {
        return new LigneHistorique("CREATION", "Intervention créée : « " + titre + " », " + adresse);
    }

    public static LigneHistorique ligneReponse(String technicien, Issue issue, BigDecimal montant, String motif) {
        String texte = switch (issue) {
            case ACCEPTEE -> technicien + " a accepté";
            case ACCEPTEE_AVEC_FRAIS -> technicien + " a accepté avec " + euros(montant) + " de frais (" + motif + ")";
            case REFUSEE -> technicien + " a refusé";
            case EN_ATTENTE, SANS_REPONSE -> throw new IllegalArgumentException("Issue de réponse invalide : " + issue);
        };
        return new LigneHistorique("REPONSE:" + technicien, texte);
    }

    public static LigneHistorique ligneSansReponse(String technicien) {
        return new LigneHistorique("SANS_REPONSE:" + technicien, technicien + " n'a pas répondu");
    }

    public static LigneHistorique ligneCloture(ResultatCloture resultat) {
        String texte = resultat.nombreAcceptations() > 0
                ? "Consultation close : " + resultat.nombreAcceptations() + " acceptation(s)"
                : "Consultation close : aucune acceptation, intervention refusée";
        return new LigneHistorique("CLOTURE", texte);
    }

    public static LigneHistorique ligneSelection(String technicien) {
        return new LigneHistorique("SELECTION", technicien + " a été sélectionné");
    }

    public static LigneHistorique ligneEtape(Etape etape, String technicien, String rapport) {
        String texte = switch (etape) {
            case EN_ROUTE -> technicien + " est en route";
            case SUR_SITE -> technicien + " est sur site";
            case TERMINEE -> "Intervention terminée — rapport : « " + rapport + " »";
        };
        return new LigneHistorique(etape.name(), texte);
    }

    public static LigneHistorique ligneFin() {
        return new LigneHistorique("FIN", "Intervention clôturée par le chef");
    }

    // --- Utilitaires ---

    private static boolean texteValide(String texte) {
        return texte != null && !texte.isBlank() && texte.length() <= LONGUEUR_MAX;
    }

    private static String euros(BigDecimal montant) {
        return montant.setScale(2, RoundingMode.UNNECESSARY).toPlainString().replace('.', ',') + " €";
    }
}
