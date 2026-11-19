package com.fieldflow.api.dto;

import com.fieldflow.regles.Etape;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** rapport n'est obligatoire que pour TERMINEE (validé par InterventionRules, R8). */
public record EtapeRequete(@NotBlank String technicien, @NotNull Etape etape, String rapport) {
}
