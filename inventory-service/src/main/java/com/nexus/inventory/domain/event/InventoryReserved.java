package com.nexus.inventory.domain.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryReserved(
        UUID orderId,
        Instant timestamp
) {}
