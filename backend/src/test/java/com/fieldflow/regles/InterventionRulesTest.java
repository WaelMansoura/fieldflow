package com.fieldflow.regles;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests des règles métier R1 à R10, écrits avant le code (principe IV). */
class InterventionRulesTest {

    private static final Instant T0 = Instant.parse("2026-11-19T09:00:00Z");
    private static final String TEXTE_200 = "a".repeat(200);
    private static final String TEXTE_201 = "a".repeat(201);

    private static Map<String, Issue> issues(Issue jean, Issue jacques, Issue pierre) {
        Map<String, Issue> m = new LinkedHashMap<>();
        m.put("Jean", jean);
        m.put("Jacques", jacques);
        m.put("Pierre", pierre);
        return m;
    }

    private static void assertRefus(Decision d, Decision.Type type, String code) {
        assertThat(d.type()).isEqualTo(type);
        assertThat(d.code()).isEqualTo(code);
        assertThat(d.message()).isNotBlank();
    }

    // T025 — création et R1
    @Nested
    class Creation {

        @Test
        void titreEtAdresseValides() {
            assertThat(InterventionRules.validerCreation("Chaudière en panne", "12 rue des Lilas").estOk()).isTrue();
            assertThat(InterventionRules.validerCreation(TEXTE_200, TEXTE_200).estOk()).isTrue();
        }

        @Test
        void titreOuAdresseVideEstInvalide() {
            assertRefus(InterventionRules.validerCreation("", "12 rue des Lilas"), Decision.Type.INVALIDE, "CHAMP_INVALIDE");
            assertRefus(InterventionRules.validerCreation("  ", "12 rue des Lilas"), Decision.Type.INVALIDE, "CHAMP_INVALIDE");
            assertRefus(InterventionRules.validerCreation(null, "12 rue des Lilas"), Decision.Type.INVALIDE, "CHAMP_INVALIDE");
            assertRefus(InterventionRules.validerCreation("Chaudière", ""), Decision.Type.INVALIDE, "CHAMP_INVALIDE");
        }

        @Test
        void titreOuAdresseDePlusDe200CaracteresEstInvalide() {
            assertRefus(InterventionRules.validerCreation(TEXTE_201, "12 rue des Lilas"), Decision.Type.INVALIDE, "CHAMP_INVALIDE");
            assertRefus(InterventionRules.validerCreation("Chaudière", TEXTE_201), Decision.Type.INVALIDE, "CHAMP_INVALIDE");
        }

        @Test
        void creationRefuseeSiUneInterventionEstActive() {
            assertThat(InterventionRules.peutCreer(false).estOk()).isTrue();
            assertRefus(InterventionRules.peutCreer(true), Decision.Type.MAUVAIS_ETAT, "INTERVENTION_ACTIVE");
        }

        @Test
        void r1_lesTroisTechniciensSontConsultesEnMemeTemps() {
            Map<String, Issue> initiales = InterventionRules.propositionsInitiales(List.of("Jean", "Jacques", "Pierre"));

            assertThat(initiales).containsOnlyKeys("Jean", "Jacques", "Pierre");
            assertThat(initiales.values()).containsOnly(Issue.EN_ATTENTE);
        }
    }

    // T026 — R5 frais
    @Nested
    class Frais {

        @Test
        void accepterAvecFraisValide() {
            assertThat(InterventionRules.validerReponse(TypeReponse.ACCEPTER_AVEC_FRAIS, new BigDecimal("40.00"), "Déplacement").estOk()).isTrue();
            assertThat(InterventionRules.validerReponse(TypeReponse.ACCEPTER_AVEC_FRAIS, new BigDecimal("0.01"), TEXTE_200).estOk()).isTrue();
        }

        @Test
        void montantNulNegatifOuAbsentEstRefuse() {
            assertRefus(InterventionRules.validerReponse(TypeReponse.ACCEPTER_AVEC_FRAIS, BigDecimal.ZERO, "Déplacement"),
                    Decision.Type.INVALIDE, "FRAIS_INVALIDES");
            assertRefus(InterventionRules.validerReponse(TypeReponse.ACCEPTER_AVEC_FRAIS, new BigDecimal("-5"), "Déplacement"),
                    Decision.Type.INVALIDE, "FRAIS_INVALIDES");
            assertRefus(InterventionRules.validerReponse(TypeReponse.ACCEPTER_AVEC_FRAIS, null, "Déplacement"),
                    Decision.Type.INVALIDE, "FRAIS_INVALIDES");
        }

        @Test
        void montantAvecPlusDeDeuxDecimalesEstRefuse() {
            assertRefus(InterventionRules.validerReponse(TypeReponse.ACCEPTER_AVEC_FRAIS, new BigDecimal("40.001"), "Déplacement"),
                    Decision.Type.INVALIDE, "FRAIS_INVALIDES");
        }

        @Test
        void motifVideOuTropLongEstRefuse() {
            assertRefus(InterventionRules.validerReponse(TypeReponse.ACCEPTER_AVEC_FRAIS, new BigDecimal("40"), ""),
                    Decision.Type.INVALIDE, "FRAIS_INVALIDES");
            assertRefus(InterventionRules.validerReponse(TypeReponse.ACCEPTER_AVEC_FRAIS, new BigDecimal("40"), null),
                    Decision.Type.INVALIDE, "FRAIS_INVALIDES");
            assertRefus(InterventionRules.validerReponse(TypeReponse.ACCEPTER_AVEC_FRAIS, new BigDecimal("40"), TEXTE_201),
                    Decision.Type.INVALIDE, "FRAIS_INVALIDES");
        }

        @Test
        void accepterEtRefuserSansFraisSontValides() {
            assertThat(InterventionRules.validerReponse(TypeReponse.ACCEPTER, null, null).estOk()).isTrue();
            assertThat(InterventionRules.validerReponse(TypeReponse.REFUSER, null, null).estOk()).isTrue();
        }

        @Test
        void typeDeReponseAbsentEstInvalide() {
            assertRefus(InterventionRules.validerReponse(null, null, null), Decision.Type.INVALIDE, "CHAMP_INVALIDE");
        }

        @Test
        void chaqueBoutonDonneSonIssue() {
            assertThat(InterventionRules.issuePour(TypeReponse.ACCEPTER)).isEqualTo(Issue.ACCEPTEE);
            assertThat(InterventionRules.issuePour(TypeReponse.ACCEPTER_AVEC_FRAIS)).isEqualTo(Issue.ACCEPTEE_AVEC_FRAIS);
            assertThat(InterventionRules.issuePour(TypeReponse.REFUSER)).isEqualTo(Issue.REFUSEE);
        }
    }

    // T027 — R4 réponse tardive
    @Nested
    class ReponseTardive {

        private final Instant echeance = T0.plusSeconds(60);

        @Test
        void reponseAccepteePendantLaConsultation() {
            assertThat(InterventionRules.peutRepondre(EtatIntervention.EN_CONSULTATION, Issue.EN_ATTENTE, T0.plusSeconds(59), echeance).estOk()).isTrue();
        }

        @Test
        void reponseRefuseeALEcheanceOuApres() {
            assertRefus(InterventionRules.peutRepondre(EtatIntervention.EN_CONSULTATION, Issue.EN_ATTENTE, echeance, echeance),
                    Decision.Type.MAUVAIS_ETAT, "CONSULTATION_CLOSE");
            assertRefus(InterventionRules.peutRepondre(EtatIntervention.EN_CONSULTATION, Issue.EN_ATTENTE, echeance.plusSeconds(1), echeance),
                    Decision.Type.MAUVAIS_ETAT, "CONSULTATION_CLOSE");
        }

        @Test
        void reponseRefuseeApresLaCloture() {
            assertRefus(InterventionRules.peutRepondre(EtatIntervention.EN_ATTENTE_SELECTION, Issue.EN_ATTENTE, T0.plusSeconds(10), echeance),
                    Decision.Type.MAUVAIS_ETAT, "CONSULTATION_CLOSE");
            assertRefus(InterventionRules.peutRepondre(EtatIntervention.REFUSEE, Issue.SANS_REPONSE, T0.plusSeconds(10), echeance),
                    Decision.Type.MAUVAIS_ETAT, "CONSULTATION_CLOSE");
        }

        @Test
        void secondeReponseRefusee() {
            assertRefus(InterventionRules.peutRepondre(EtatIntervention.EN_CONSULTATION, Issue.ACCEPTEE, T0.plusSeconds(10), echeance),
                    Decision.Type.MAUVAIS_ETAT, "DEJA_REPONDU");
        }
    }

    // T028 — R3 clôture
    @Nested
    class Cloture {

        @Test
        void consultationCompleteQuandPlusPersonneNEstEnAttente() {
            assertThat(InterventionRules.consultationComplete(issues(Issue.ACCEPTEE, Issue.REFUSEE, Issue.ACCEPTEE_AVEC_FRAIS).values())).isTrue();
            assertThat(InterventionRules.consultationComplete(issues(Issue.ACCEPTEE, Issue.REFUSEE, Issue.EN_ATTENTE).values())).isFalse();
        }

        @Test
        void auMoinsUneAcceptationMeneALaSelection() {
            ResultatCloture r = InterventionRules.clore(issues(Issue.ACCEPTEE, Issue.ACCEPTEE_AVEC_FRAIS, Issue.EN_ATTENTE));

            assertThat(r.etatFinal()).isEqualTo(EtatIntervention.EN_ATTENTE_SELECTION);
            assertThat(r.sansReponse()).containsExactly("Pierre");
            assertThat(r.nombreAcceptations()).isEqualTo(2);
        }

        @Test
        void troisRefusMenentARefusee() {
            ResultatCloture r = InterventionRules.clore(issues(Issue.REFUSEE, Issue.REFUSEE, Issue.REFUSEE));

            assertThat(r.etatFinal()).isEqualTo(EtatIntervention.REFUSEE);
            assertThat(r.sansReponse()).isEmpty();
            assertThat(r.nombreAcceptations()).isZero();
        }

        @Test
        void personneNARepondu() {
            ResultatCloture r = InterventionRules.clore(issues(Issue.EN_ATTENTE, Issue.EN_ATTENTE, Issue.EN_ATTENTE));

            assertThat(r.etatFinal()).isEqualTo(EtatIntervention.REFUSEE);
            assertThat(r.sansReponse()).containsExactly("Jean", "Jacques", "Pierre");
        }

        @Test
        void deuxRefusEtUnSansReponse() {
            ResultatCloture r = InterventionRules.clore(issues(Issue.REFUSEE, Issue.EN_ATTENTE, Issue.REFUSEE));

            assertThat(r.etatFinal()).isEqualTo(EtatIntervention.REFUSEE);
            assertThat(r.sansReponse()).containsExactly("Jacques");
        }
    }

    // T029 — R6 sélection unique
    @Nested
    class Selection {

        @Test
        void selectionDUnePropositionAcceptee() {
            assertThat(InterventionRules.peutSelectionner(EtatIntervention.EN_ATTENTE_SELECTION, Issue.ACCEPTEE).estOk()).isTrue();
            assertThat(InterventionRules.peutSelectionner(EtatIntervention.EN_ATTENTE_SELECTION, Issue.ACCEPTEE_AVEC_FRAIS).estOk()).isTrue();
        }

        @Test
        void selectionDUnePropositionNonAccepteeRefusee() {
            assertRefus(InterventionRules.peutSelectionner(EtatIntervention.EN_ATTENTE_SELECTION, Issue.REFUSEE),
                    Decision.Type.MAUVAIS_ETAT, "SELECTION_IMPOSSIBLE");
            assertRefus(InterventionRules.peutSelectionner(EtatIntervention.EN_ATTENTE_SELECTION, Issue.SANS_REPONSE),
                    Decision.Type.MAUVAIS_ETAT, "SELECTION_IMPOSSIBLE");
        }

        @Test
        void secondeSelectionOuSelectionAvantClotureRefusee() {
            assertRefus(InterventionRules.peutSelectionner(EtatIntervention.SELECTIONNEE, Issue.ACCEPTEE),
                    Decision.Type.MAUVAIS_ETAT, "SELECTION_IMPOSSIBLE");
            assertRefus(InterventionRules.peutSelectionner(EtatIntervention.EN_CONSULTATION, Issue.ACCEPTEE),
                    Decision.Type.MAUVAIS_ETAT, "SELECTION_IMPOSSIBLE");
        }
    }

    // T030 — R3 et R7 : vue du technicien (data-model.md, « Vue du technicien »)
    @Nested
    class Vue {

        @Test
        void aucuneInterventionCourante() {
            assertThat(InterventionRules.vueTechnicien(null, null, false)).isEqualTo(VueTechnicien.VIDE);
        }

        @Test
        void pendantLaConsultation() {
            assertThat(InterventionRules.vueTechnicien(EtatIntervention.EN_CONSULTATION, Issue.EN_ATTENTE, false)).isEqualTo(VueTechnicien.CONSULTATION);
            assertThat(InterventionRules.vueTechnicien(EtatIntervention.EN_CONSULTATION, Issue.ACCEPTEE, false)).isEqualTo(VueTechnicien.ATTENTE);
            assertThat(InterventionRules.vueTechnicien(EtatIntervention.EN_CONSULTATION, Issue.REFUSEE, false)).isEqualTo(VueTechnicien.ATTENTE);
        }

        @Test
        void enAttenteDeSelection() {
            assertThat(InterventionRules.vueTechnicien(EtatIntervention.EN_ATTENTE_SELECTION, Issue.ACCEPTEE, false)).isEqualTo(VueTechnicien.ATTENTE);
            assertThat(InterventionRules.vueTechnicien(EtatIntervention.EN_ATTENTE_SELECTION, Issue.ACCEPTEE_AVEC_FRAIS, false)).isEqualTo(VueTechnicien.ATTENTE);
            assertThat(InterventionRules.vueTechnicien(EtatIntervention.EN_ATTENTE_SELECTION, Issue.REFUSEE, false)).isEqualTo(VueTechnicien.VIDE);
            assertThat(InterventionRules.vueTechnicien(EtatIntervention.EN_ATTENTE_SELECTION, Issue.SANS_REPONSE, false)).isEqualTo(VueTechnicien.VIDE);
        }

        @Test
        void apresLaSelectionSeulLeSelectionneVoitLIntervention() {
            for (EtatIntervention etat : List.of(EtatIntervention.SELECTIONNEE, EtatIntervention.EN_ROUTE, EtatIntervention.SUR_SITE)) {
                assertThat(InterventionRules.vueTechnicien(etat, Issue.ACCEPTEE, true)).isEqualTo(VueTechnicien.ETAPES);
                assertThat(InterventionRules.vueTechnicien(etat, Issue.ACCEPTEE, false)).isEqualTo(VueTechnicien.VIDE);
                assertThat(InterventionRules.vueTechnicien(etat, Issue.SANS_REPONSE, false)).isEqualTo(VueTechnicien.VIDE);
            }
        }

        @Test
        void refuseeTousLesTechniciensSurLEcranVide() {
            assertThat(InterventionRules.vueTechnicien(EtatIntervention.REFUSEE, Issue.REFUSEE, false)).isEqualTo(VueTechnicien.VIDE);
            assertThat(InterventionRules.vueTechnicien(EtatIntervention.REFUSEE, Issue.SANS_REPONSE, false)).isEqualTo(VueTechnicien.VIDE);
            assertThat(InterventionRules.vueTechnicien(EtatIntervention.REFUSEE, Issue.EN_ATTENTE, false)).isEqualTo(VueTechnicien.VIDE);
        }

        @Test
        void termineeEcranVide() {
            assertThat(InterventionRules.vueTechnicien(EtatIntervention.TERMINEE, Issue.ACCEPTEE, true)).isEqualTo(VueTechnicien.VIDE);
        }
    }

    // T056 — R8 étapes
    @Nested
    class Etapes {

        @Test
        void lesEtapesSeSuiventDansLOrdre() {
            assertThat(InterventionRules.etapeSuivante(EtatIntervention.SELECTIONNEE)).isEqualTo(Etape.EN_ROUTE);
            assertThat(InterventionRules.etapeSuivante(EtatIntervention.EN_ROUTE)).isEqualTo(Etape.SUR_SITE);
            assertThat(InterventionRules.etapeSuivante(EtatIntervention.SUR_SITE)).isEqualTo(Etape.TERMINEE);
            assertThat(InterventionRules.etapeSuivante(EtatIntervention.TERMINEE)).isNull();
            assertThat(InterventionRules.etapeSuivante(EtatIntervention.EN_CONSULTATION)).isNull();
        }

        @Test
        void chaqueEtapeMeneASonEtat() {
            assertThat(InterventionRules.etatApres(Etape.EN_ROUTE)).isEqualTo(EtatIntervention.EN_ROUTE);
            assertThat(InterventionRules.etatApres(Etape.SUR_SITE)).isEqualTo(EtatIntervention.SUR_SITE);
            assertThat(InterventionRules.etatApres(Etape.TERMINEE)).isEqualTo(EtatIntervention.TERMINEE);
        }

        @Test
        void etapeSuivanteDuTechnicienSelectionneAcceptee() {
            assertThat(InterventionRules.peutPasserEtape(EtatIntervention.SELECTIONNEE, Etape.EN_ROUTE, "Jean", "Jean", null).estOk()).isTrue();
            assertThat(InterventionRules.peutPasserEtape(EtatIntervention.EN_ROUTE, Etape.SUR_SITE, "Jean", "Jean", null).estOk()).isTrue();
            assertThat(InterventionRules.peutPasserEtape(EtatIntervention.SUR_SITE, Etape.TERMINEE, "Jean", "Jean", "Brûleur remplacé").estOk()).isTrue();
        }

        @Test
        void etapeHorsOrdreOuRepeteeRefusee() {
            assertRefus(InterventionRules.peutPasserEtape(EtatIntervention.SELECTIONNEE, Etape.SUR_SITE, "Jean", "Jean", null),
                    Decision.Type.MAUVAIS_ETAT, "ETAPE_HORS_ORDRE");
            assertRefus(InterventionRules.peutPasserEtape(EtatIntervention.EN_ROUTE, Etape.EN_ROUTE, "Jean", "Jean", null),
                    Decision.Type.MAUVAIS_ETAT, "ETAPE_HORS_ORDRE");
            assertRefus(InterventionRules.peutPasserEtape(EtatIntervention.SUR_SITE, Etape.EN_ROUTE, "Jean", "Jean", null),
                    Decision.Type.MAUVAIS_ETAT, "ETAPE_HORS_ORDRE");
            assertRefus(InterventionRules.peutPasserEtape(EtatIntervention.TERMINEE, Etape.TERMINEE, "Jean", "Jean", "Rapport"),
                    Decision.Type.MAUVAIS_ETAT, "ETAPE_HORS_ORDRE");
        }

        @Test
        void technicienNonSelectionneRefuse() {
            assertRefus(InterventionRules.peutPasserEtape(EtatIntervention.SELECTIONNEE, Etape.EN_ROUTE, "Jacques", "Jean", null),
                    Decision.Type.MAUVAIS_ETAT, "NON_SELECTIONNE");
        }

        @Test
        void rapportObligatoirePourTerminer() {
            assertRefus(InterventionRules.peutPasserEtape(EtatIntervention.SUR_SITE, Etape.TERMINEE, "Jean", "Jean", ""),
                    Decision.Type.INVALIDE, "RAPPORT_OBLIGATOIRE");
            assertRefus(InterventionRules.peutPasserEtape(EtatIntervention.SUR_SITE, Etape.TERMINEE, "Jean", "Jean", "   "),
                    Decision.Type.INVALIDE, "RAPPORT_OBLIGATOIRE");
            assertRefus(InterventionRules.peutPasserEtape(EtatIntervention.SUR_SITE, Etape.TERMINEE, "Jean", "Jean", null),
                    Decision.Type.INVALIDE, "RAPPORT_OBLIGATOIRE");
        }

        @Test
        void etapeAbsenteInvalide() {
            assertRefus(InterventionRules.peutPasserEtape(EtatIntervention.SELECTIONNEE, null, "Jean", "Jean", null),
                    Decision.Type.INVALIDE, "CHAMP_INVALIDE");
        }
    }

    // T056 — R10 Finir
    @Nested
    class Finir {

        @Test
        void finirPossibleSeulementTermineeOuRefusee() {
            assertThat(InterventionRules.peutFinir(EtatIntervention.TERMINEE).estOk()).isTrue();
            assertThat(InterventionRules.peutFinir(EtatIntervention.REFUSEE).estOk()).isTrue();
            for (EtatIntervention etat : List.of(EtatIntervention.EN_CONSULTATION, EtatIntervention.EN_ATTENTE_SELECTION,
                    EtatIntervention.SELECTIONNEE, EtatIntervention.EN_ROUTE, EtatIntervention.SUR_SITE)) {
                assertRefus(InterventionRules.peutFinir(etat), Decision.Type.MAUVAIS_ETAT, "FINIR_IMPOSSIBLE");
            }
        }
    }

    // T031 — R2 délai
    @Nested
    class Delai {

        @Test
        void echeanceEgaleDebutPlusDelai() {
            assertThat(InterventionRules.echeance(T0, Duration.ofSeconds(60))).isEqualTo(T0.plusSeconds(60));
            assertThat(InterventionRules.echeance(T0, Duration.ofSeconds(30))).isEqualTo(T0.plusSeconds(30));
        }
    }

    // T032 — R9 lignes d'historique (data-model.md, tableau « cle »)
    @Nested
    class Historique {

        @Test
        void creation() {
            assertThat(InterventionRules.ligneCreation("Chaudière en panne", "12 rue des Lilas"))
                    .isEqualTo(new LigneHistorique("CREATION", "Intervention créée : « Chaudière en panne », 12 rue des Lilas"));
        }

        @Test
        void reponses() {
            assertThat(InterventionRules.ligneReponse("Jean", Issue.ACCEPTEE, null, null))
                    .isEqualTo(new LigneHistorique("REPONSE:Jean", "Jean a accepté"));
            assertThat(InterventionRules.ligneReponse("Jacques", Issue.ACCEPTEE_AVEC_FRAIS, new BigDecimal("40"), "Déplacement"))
                    .isEqualTo(new LigneHistorique("REPONSE:Jacques", "Jacques a accepté avec 40,00 € de frais (Déplacement)"));
            assertThat(InterventionRules.ligneReponse("Pierre", Issue.REFUSEE, null, null))
                    .isEqualTo(new LigneHistorique("REPONSE:Pierre", "Pierre a refusé"));
        }

        @Test
        void sansReponse() {
            assertThat(InterventionRules.ligneSansReponse("Pierre"))
                    .isEqualTo(new LigneHistorique("SANS_REPONSE:Pierre", "Pierre n'a pas répondu"));
        }

        @Test
        void cloture() {
            assertThat(InterventionRules.ligneCloture(new ResultatCloture(EtatIntervention.EN_ATTENTE_SELECTION, List.of("Pierre"), 2)))
                    .isEqualTo(new LigneHistorique("CLOTURE", "Consultation close : 2 acceptation(s)"));
            assertThat(InterventionRules.ligneCloture(new ResultatCloture(EtatIntervention.REFUSEE, List.of(), 0)))
                    .isEqualTo(new LigneHistorique("CLOTURE", "Consultation close : aucune acceptation, intervention refusée"));
        }

        @Test
        void selection() {
            assertThat(InterventionRules.ligneSelection("Jean"))
                    .isEqualTo(new LigneHistorique("SELECTION", "Jean a été sélectionné"));
        }

        @Test
        void etapes() {
            assertThat(InterventionRules.ligneEtape(Etape.EN_ROUTE, "Jean", null))
                    .isEqualTo(new LigneHistorique("EN_ROUTE", "Jean est en route"));
            assertThat(InterventionRules.ligneEtape(Etape.SUR_SITE, "Jean", null))
                    .isEqualTo(new LigneHistorique("SUR_SITE", "Jean est sur site"));
            assertThat(InterventionRules.ligneEtape(Etape.TERMINEE, "Jean", "Brûleur remplacé"))
                    .isEqualTo(new LigneHistorique("TERMINEE", "Intervention terminée — rapport : « Brûleur remplacé »"));
        }

        @Test
        void fin() {
            assertThat(InterventionRules.ligneFin())
                    .isEqualTo(new LigneHistorique("FIN", "Intervention clôturée par le chef"));
        }
    }
}
