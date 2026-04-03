package com.nexus.order.domain.enums;

public enum OrderStatus {
    PENDING,
    PAYMENT_REQUESTED,
    PAYMENT_COMPLETED,
    INVENTORY_REQUESTED,
    CONFIRMED,
    CANCELLED,
    REFUND_REQUESTED
}
