package com.nexus.inventory.domain.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryInsufficient(
        UUID orderId,
        UUID productId,
        String reason,
        Instant timestamp
) {}
