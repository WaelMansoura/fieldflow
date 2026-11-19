package com.fieldflow.config;

import io.dapr.spring.workflows.config.EnableDaprWorkflows;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Enregistre les workflows et activités Dapr et démarre le moteur de workflow.
 * Désactivé avec le profil "test" : les tests d'intégration démarrent sans sidecar.
 */
@Configuration
@EnableDaprWorkflows
@Profile("!test")
public class DaprWorkflowsConfig {
}
