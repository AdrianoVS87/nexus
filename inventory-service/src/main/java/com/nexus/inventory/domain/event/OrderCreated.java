package com.nexus.inventory.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Mapped to avoid deserialization failures on shared "orders" topic. */
public record OrderCreated(UUID orderId, UUID userId, Instant timestamp) {}
