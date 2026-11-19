package com.fieldflow.workflow.activites;

import io.dapr.client.DaprClient;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Publie un changement sur Dapr Pub/Sub. Une erreur de publication est journalisée et n'est pas
 * propagée : l'écriture en base est déjà faite et le rafraîchissement de repli (2 s) met les écrans
 * à jour (research R-09).
 */
@Component
public class Notificateur {

    static final String PUBSUB = "pubsub";
    static final String TOPIC = "intervention-events";

    private static final Logger LOG = LoggerFactory.getLogger(Notificateur.class);

    private final DaprClient daprClient;

    public Notificateur(DaprClient daprClient) {
        this.daprClient = daprClient;
    }

    public void notifier(UUID interventionId, String type) {
        try {
            daprClient.publishEvent(PUBSUB, TOPIC, new Notification(interventionId, type)).block();
        } catch (RuntimeException e) {
            LOG.warn("Publication {} pour {} impossible : {}", type, interventionId, e.getMessage());
        }
    }
}
