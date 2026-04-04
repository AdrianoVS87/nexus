package com.nexus.notification.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Received but not acted upon by notification service.
 * Mapped to avoid deserialization failures on shared "orders" topic.
 */
public record OrderCreated(
        UUID orderId,
        UUID userId,
        Instant timestamp
) {}
