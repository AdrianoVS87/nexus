package com.nexus.order.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order lifecycle states with explicit valid transitions.
 *
 * <p>Each status declares the set of states it may transition to.
 * Callers must use {@link #canTransitionTo(OrderStatus)} before
 * mutating an order's status to guarantee saga consistency.</p>
 */
public enum OrderStatus {

    PENDING(/* → */ "PAYMENT_REQUESTED", "CANCELLED"),
    PAYMENT_REQUESTED(/* → */ "PAYMENT_COMPLETED", "CANCELLED"),
    PAYMENT_COMPLETED(/* → */ "INVENTORY_REQUESTED"),
    INVENTORY_REQUESTED(/* → */ "CONFIRMED", "REFUND_REQUESTED"),
    CONFIRMED(/* → */ "CANCELLED"),
    CANCELLED(),
    REFUND_REQUESTED(/* → */ "CANCELLED");

    private Set<OrderStatus> validTransitions;

    OrderStatus(String... targets) {
        // Deferred resolution: enum constants aren't fully initialized
        // during their own constructor, so we store names and resolve lazily.
        this.validTransitions = null;
        this.targetNames = targets;
    }

    private final String[] targetNames;

    /**
     * Returns whether transitioning from this status to {@code target} is valid.
     */
    public boolean canTransitionTo(OrderStatus target) {
        return getAllowedTransitions().contains(target);
    }

    /**
     * Returns the immutable set of states reachable from this status.
     */
    public Set<OrderStatus> getAllowedTransitions() {
        if (validTransitions == null) {
            if (targetNames.length == 0) {
                validTransitions = EnumSet.noneOf(OrderStatus.class);
            } else {
                EnumSet<OrderStatus> set = EnumSet.noneOf(OrderStatus.class);
                for (String name : targetNames) {
                    set.add(OrderStatus.valueOf(name));
                }
                validTransitions = set;
            }
        }
        return validTransitions;
    }
}
