package com.nexus.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record Notification(
        UUID id,
        UUID orderId,
        UUID userId,
        String type,
        String channel,
        String message,
        Instant timestamp
) {}
