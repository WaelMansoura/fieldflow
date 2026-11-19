package com.fieldflow.workflow;

import java.util.List;
import java.util.UUID;

/** Entrée de InterventionWorkflow (contracts/workflow.md). Le délai est fixé à la création (research R-02). */
public record EntreeWorkflow(UUID interventionId, String titre, String adresse, List<String> techniciens,
                             long delaiSecondes) {
}
