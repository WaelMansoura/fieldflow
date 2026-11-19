package com.fieldflow.api.dto;

import com.fieldflow.regles.Issue;
import java.math.BigDecimal;

public record PropositionDto(String technicien, Issue issue, BigDecimal montantFrais, String motifFrais,
                             boolean selectionnable) {
}
