package com.nexus.order.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.nexus.order.domain.enums.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderStatus state machine")
class OrderStatusTest {

    @ParameterizedTest(name = "{0} → {1} should be allowed")
    @CsvSource({
            "PENDING, PAYMENT_REQUESTED",
            "PENDING, CANCELLED",
            "PAYMENT_REQUESTED, PAYMENT_COMPLETED",
            "PAYMENT_REQUESTED, CANCELLED",
            "PAYMENT_COMPLETED, INVENTORY_REQUESTED",
            "INVENTORY_REQUESTED, CONFIRMED",
            "INVENTORY_REQUESTED, REFUND_REQUESTED",
            "REFUND_REQUESTED, CANCELLED",
    })
    void validTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to))
                .as("%s → %s", from, to)
                .isTrue();
    }

    @ParameterizedTest(name = "{0} → {1} should be rejected")
    @CsvSource({
            "PENDING, CONFIRMED",
            "PENDING, INVENTORY_REQUESTED",
            "PAYMENT_REQUESTED, CONFIRMED",
            "PAYMENT_COMPLETED, CANCELLED",
            "CONFIRMED, CANCELLED",
            "CANCELLED, PENDING",
            "CONFIRMED, PENDING",
    })
    void invalidTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to))
                .as("%s → %s", from, to)
                .isFalse();
    }

    @Test
    @DisplayName("Terminal states have no outgoing transitions")
    void terminalStatesHaveNoTransitions() {
        assertThat(CONFIRMED.getAllowedTransitions()).isEmpty();
        assertThat(CANCELLED.getAllowedTransitions()).isEmpty();
    }
}
