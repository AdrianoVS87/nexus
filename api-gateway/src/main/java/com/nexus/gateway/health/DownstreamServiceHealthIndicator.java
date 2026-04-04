package com.nexus.gateway.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Component("downstreamServices")
public class DownstreamServiceHealthIndicator implements ReactiveHealthIndicator {

    private final WebClient webClient;
    private final String orderServiceUrl;
    private final String inventoryServiceUrl;
    private final String notificationServiceUrl;

    public DownstreamServiceHealthIndicator(
            WebClient.Builder webClientBuilder,
            @Value("${ORDER_SERVICE_URL:http://localhost:8081}") String orderServiceUrl,
            @Value("${INVENTORY_SERVICE_URL:http://localhost:8083}") String inventoryServiceUrl,
            @Value("${NOTIFICATION_SERVICE_URL:http://localhost:8084}") String notificationServiceUrl) {
        this.webClient = webClientBuilder.build();
        this.orderServiceUrl = orderServiceUrl;
        this.inventoryServiceUrl = inventoryServiceUrl;
        this.notificationServiceUrl = notificationServiceUrl;
    }

    @Override
    public Mono<Health> health() {
        return Mono.zip(
                checkService("order-service", orderServiceUrl),
                checkService("inventory-service", inventoryServiceUrl),
                checkService("notification-service", notificationServiceUrl)
        ).map(tuple -> {
            Health.Builder builder = Health.up();
            builder.withDetail("order-service", tuple.getT1());
            builder.withDetail("inventory-service", tuple.getT2());
            builder.withDetail("notification-service", tuple.getT3());

            if ("DOWN".equals(tuple.getT1().get("status"))
                    && "DOWN".equals(tuple.getT2().get("status"))
                    && "DOWN".equals(tuple.getT3().get("status"))) {
                builder.down();
            }
            return builder.build();
        });
    }

    private Mono<Map<String, String>> checkService(String name, String baseUrl) {
        return webClient.get()
                .uri(baseUrl + "/actuator/health")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(3))
                .map(body -> Map.of("status", "UP"))
                .onErrorReturn(Map.of("status", "DOWN"));
    }
}
