package com.nexus.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCompleted(UUID paymentId, UUID orderId, BigDecimal amount, Instant timestamp) {}
