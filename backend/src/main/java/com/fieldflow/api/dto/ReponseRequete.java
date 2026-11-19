package com.fieldflow.api.dto;

import com.fieldflow.regles.TypeReponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** montant et motif ne sont attendus que pour ACCEPTER_AVEC_FRAIS (validés par InterventionRules, R5). */
public record ReponseRequete(@NotBlank String technicien, @NotNull TypeReponse type, BigDecimal montant, String motif) {
}
