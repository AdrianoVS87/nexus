package com.nexus.order.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRequested(
        UUID orderId,
        UUID userId,
        BigDecimal amount,
        String currency,
        String idempotencyKey,
        Instant timestamp
) {}
