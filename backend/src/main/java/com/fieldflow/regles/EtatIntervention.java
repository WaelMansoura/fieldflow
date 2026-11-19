package com.fieldflow.regles;

public enum EtatIntervention {
    EN_CONSULTATION("En consultation"),
    EN_ATTENTE_SELECTION("En attente de sélection"),
    SELECTIONNEE("Sélectionnée"),
    EN_ROUTE("En route"),
    SUR_SITE("Sur site"),
    TERMINEE("Terminée"),
    REFUSEE("Refusée");

    private final String libelle;

    EtatIntervention(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
