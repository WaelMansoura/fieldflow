package com.fieldflow.regles;

import java.util.List;

/** Résultat de la clôture de la consultation (R3). */
public record ResultatCloture(EtatIntervention etatFinal, List<String> sansReponse, int nombreAcceptations) {
}
