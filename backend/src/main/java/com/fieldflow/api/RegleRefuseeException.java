package com.fieldflow.api;

import com.fieldflow.regles.Decision;

/** Levée quand InterventionRules refuse une action ; traduite en 400, 404 ou 409 par GestionErreurs. */
public class RegleRefuseeException extends RuntimeException {

    private final Decision decision;

    public RegleRefuseeException(Decision decision) {
        super(decision.message());
        this.decision = decision;
    }

    public Decision decision() {
        return decision;
    }

    /** Lève l'exception si la décision n'est pas OK. */
    public static void verifier(Decision decision) {
        if (!decision.estOk()) {
            throw new RegleRefuseeException(decision);
        }
    }
}
