package com.fieldflow.regles;

/** Ligne d'historique (R9) : clé d'idempotence et description lisible. L'heure est fournie à part. */
public record LigneHistorique(String cle, String description) {
}
