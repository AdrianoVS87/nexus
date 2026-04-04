package com.nexus.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies that the reactive security chain permits access to public endpoints
 * and blocks nothing unintentionally while auth is disabled.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "spring.cloud.gateway.routes[0].id=test-route",
        "spring.cloud.gateway.routes[0].uri=http://localhost:9999",
        "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/v1/orders/**",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=16379",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
        "spring.cloud.gateway.routes[0].filters[0].name=StripPrefix",
        "spring.cloud.gateway.routes[0].filters[0].args.parts=0",
        "management.endpoint.health.show-details=never"
})
class SecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void publicEndpoints_shouldBeAccessible() {
        webTestClient.post()
                .uri("/api/v1/auth/token")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void actuatorEndpoints_shouldBeAccessible() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
