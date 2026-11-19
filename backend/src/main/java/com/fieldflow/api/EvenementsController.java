package com.fieldflow.api;

import com.fieldflow.workflow.activites.Notification;
import io.dapr.Topic;
import io.dapr.client.domain.CloudEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pousse chaque notification Pub/Sub aux navigateurs par Server-Sent Events (research R-09).
 * Les connexions ouvertes ne sont pas des données de workflow : après un redémarrage, le navigateur
 * se reconnecte et recharge sa vue.
 */
@RestController
public class EvenementsController {

    private final List<SseEmitter> emetteurs = new CopyOnWriteArrayList<>();

    /**
     * Battement toutes les 5 s : le navigateur détecte ainsi une connexion morte (serveur arrêté derrière
     * un proxy qui garde la connexion ouverte) et passe en repli puis se reconnecte.
     */
    private final ScheduledExecutorService battement = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    void demarrerBattement() {
        battement.scheduleAtFixedRate(() -> envoyer(SseEmitter.event().name("ping").data("")), 5, 5, TimeUnit.SECONDS);
    }

    @PreDestroy
    void arreterBattement() {
        battement.shutdownNow();
    }

    @GetMapping(path = "/api/evenements", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ouvrir() {
        SseEmitter emetteur = new SseEmitter(0L);
        emetteurs.add(emetteur);
        emetteur.onCompletion(() -> emetteurs.remove(emetteur));
        emetteur.onTimeout(() -> emetteurs.remove(emetteur));
        emetteur.onError(e -> emetteurs.remove(emetteur));
        return emetteur;
    }

    @Topic(name = "intervention-events", pubsubName = "pubsub")
    @PostMapping("/dapr/intervention-events")
    public void recevoir(@RequestBody CloudEvent<Notification> evenement) {
        diffuser(evenement.getData());
    }

    void diffuser(Notification notification) {
        envoyer(SseEmitter.event().name("intervention").data(notification, MediaType.APPLICATION_JSON));
    }

    private void envoyer(SseEmitter.SseEventBuilder evenement) {
        for (SseEmitter emetteur : emetteurs) {
            try {
                emetteur.send(evenement);
            } catch (IOException | IllegalStateException e) {
                emetteurs.remove(emetteur);
            }
        }
    }
}
