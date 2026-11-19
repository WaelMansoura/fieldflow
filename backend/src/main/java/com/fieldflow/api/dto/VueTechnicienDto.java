package com.fieldflow.api.dto;

import com.fieldflow.regles.Etape;
import com.fieldflow.regles.EtatIntervention;
import com.fieldflow.regles.Issue;
import com.fieldflow.regles.VueTechnicien;
import java.util.UUID;

/** Vue d'un technicien (contracts/api-rest.md, GET /api/techniciens/{nom}/vue). */
public record VueTechnicienDto(VueTechnicien vue, UUID interventionId, String titre, String adresse,
                               Long secondesRestantes, Issue maReponse, EtatIntervention etat, Etape etapeSuivante) {

    public static VueTechnicienDto vide() {
        return new VueTechnicienDto(VueTechnicien.VIDE, null, null, null, null, null, null, null);
    }
}
