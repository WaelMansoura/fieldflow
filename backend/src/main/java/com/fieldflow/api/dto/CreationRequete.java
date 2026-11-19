package com.fieldflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** intervention.titre et intervention.adresse (data-model.md) : obligatoires, 200 caractères au maximum. */
public record CreationRequete(@NotBlank @Size(max = 200) String titre, @NotBlank @Size(max = 200) String adresse) {
}
