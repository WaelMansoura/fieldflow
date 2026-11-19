package com.fieldflow;

import io.dapr.client.DaprClient;
import io.dapr.workflows.client.DaprWorkflowClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base des tests d'intégration : vraie base PostgreSQL 16 (Testcontainers), sans sidecar Dapr.
 * Le profil "test" désactive DaprWorkflowsConfig (aucun moteur de workflow démarré) et les clients
 * Dapr sont remplacés par des mocks (aucun appel gRPC).
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        // Un seul conteneur pour toutes les classes de test (le contexte Spring est mis en cache).
        POSTGRES.start();
    }

    @MockitoBean
    protected DaprWorkflowClient workflowClient;

    @MockitoBean
    protected DaprClient daprClient;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void viderLesTables() {
        jdbc.execute("TRUNCATE evenement_historique, proposition, intervention");
    }
}
