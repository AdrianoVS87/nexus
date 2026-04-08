package com.nexus.order.health;

import com.nexus.order.domain.enums.OrderStatus;
import com.nexus.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Custom health indicator that exposes saga statistics in the
 * /actuator/health endpoint, giving operators real-time visibility
 * into order flow distribution.
 */
@Component("sagaHealth")
@RequiredArgsConstructor
public class SagaHealthIndicator implements HealthIndicator {

    private final OrderRepository orderRepository;

    @Override
    public Health health() {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status, orderRepository.countByStatus(status));
        }

        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long stuck = counts.getOrDefault(OrderStatus.PAYMENT_REQUESTED, 0L)
                   + counts.getOrDefault(OrderStatus.INVENTORY_REQUESTED, 0L);

        var builder = (stuck > 100) ? Health.down() : Health.up();

        return builder
                .withDetail("totalOrders", total)
                .withDetail("ordersByStatus", counts)
                .withDetail("stuckInProgress", stuck)
                .build();
    }
}
