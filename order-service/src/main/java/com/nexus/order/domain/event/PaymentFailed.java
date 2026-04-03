package com.nexus.order.domain.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailed(
        UUID orderId,
        String reason,
        Instant timestamp
) {}
