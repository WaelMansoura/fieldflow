package com.fieldflow.workflow.activites;

import java.util.UUID;

/**
 * Message publié sur le topic "intervention-events". Il ne porte pas l'état : l'écran recharge sa vue,
 * ce qui rend un message reçu en double sans effet (research R-09).
 */
public record Notification(UUID interventionId, String type) {
}
