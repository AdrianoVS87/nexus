package com.nexus.order.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreated(
        UUID orderId,
        UUID userId,
        BigDecimal totalAmount,
        String currency,
        List<OrderItemPayload> items,
        Instant timestamp
) {
    public record OrderItemPayload(UUID productId, String productName, int quantity, BigDecimal unitPrice) {}
}
