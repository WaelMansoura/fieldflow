package com.fieldflow.api.dto;

import com.fieldflow.regles.EtatIntervention;
import java.util.List;
import java.util.UUID;

/** Vue du chef (contracts/api-rest.md, GET /api/interventions/courante). */
public record InterventionCouranteDto(UUID id, String titre, String adresse, EtatIntervention etat, String etatLibelle,
                                      long secondesRestantes, List<PropositionDto> propositions,
                                      List<EvenementDto> historique, boolean finissable) {
}
