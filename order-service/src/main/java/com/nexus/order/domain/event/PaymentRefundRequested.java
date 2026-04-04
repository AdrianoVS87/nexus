package com.nexus.order.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRefundRequested(
        UUID orderId,
        UUID userId,
        BigDecimal amount,
        String reason,
        Instant timestamp
) {}
