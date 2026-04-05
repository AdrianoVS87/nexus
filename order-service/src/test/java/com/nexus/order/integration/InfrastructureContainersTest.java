package com.nexus.order.integration;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.DriverManager;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for external infrastructure dependencies using Testcontainers.
 *
 * <p>Ensures local/CI environments can boot PostgreSQL + Kafka containers,
 * establish JDBC connectivity, and perform basic Kafka admin operations.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class InfrastructureContainersTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("nexus")
            .withUsername("nexus")
            .withPassword("nexus");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @Test
    @DisplayName("PostgreSQL and Kafka containers start and accept connections")
    void infrastructureIsReachable() throws Exception {
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            assertThat(connection.isValid(2)).isTrue();
        }

        try (var admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            var topics = admin.listTopics().names().get();
            assertThat(topics).isNotNull();
        }
    }
}
