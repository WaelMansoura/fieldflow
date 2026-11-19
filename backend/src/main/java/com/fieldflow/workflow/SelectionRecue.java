package com.fieldflow.workflow;

import java.time.Instant;

/** Événement externe "selection". */
public record SelectionRecue(String technicien, Instant recuA) {
}
