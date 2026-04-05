package com.nexus.common.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryReserveRequested(
        UUID orderId,
        List<ReservationItem> items,
        Instant timestamp
) {
    public record ReservationItem(UUID productId, int quantity) {}
}
