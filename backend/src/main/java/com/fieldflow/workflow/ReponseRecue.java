package com.fieldflow.workflow;

import com.fieldflow.regles.TypeReponse;
import java.math.BigDecimal;
import java.time.Instant;

/** Événement externe "reponse". recuA : heure prise par le contrôleur (research R-06). */
public record ReponseRecue(String technicien, TypeReponse type, BigDecimal montant, String motif, Instant recuA) {
}
