package com.fieldflow.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SelectionRequete(@NotBlank String technicien) {
}
