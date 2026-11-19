package com.fieldflow.workflow;

import com.fieldflow.regles.Etape;
import java.time.Instant;

/** Événement externe "etape". */
public record EtapeRecue(String technicien, Etape etape, String rapport, Instant recuA) {
}
