package com.nexus.common.event;

import java.time.Instant;
import java.util.UUID;

public record OrderConfirmed(UUID orderId, UUID userId, Instant timestamp) {}
