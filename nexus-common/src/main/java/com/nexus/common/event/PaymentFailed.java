package com.nexus.common.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailed(UUID orderId, UUID userId, String reason, Instant timestamp) {}
