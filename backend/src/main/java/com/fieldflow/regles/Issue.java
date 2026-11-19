package com.fieldflow.regles;

/** Issue de la proposition d'un technicien. */
public enum Issue {
    EN_ATTENTE,
    ACCEPTEE,
    ACCEPTEE_AVEC_FRAIS,
    REFUSEE,
    SANS_REPONSE;

    public boolean estAcceptee() {
        return this == ACCEPTEE || this == ACCEPTEE_AVEC_FRAIS;
    }
}
