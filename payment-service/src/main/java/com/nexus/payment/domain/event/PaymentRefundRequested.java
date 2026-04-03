package com.nexus.payment.domain.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentRefundRequested(
        UUID orderId,
        String reason,
        Instant timestamp
) {}
