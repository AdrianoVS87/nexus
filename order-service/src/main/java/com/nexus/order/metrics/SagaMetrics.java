package com.nexus.order.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Business-level Micrometer metrics for saga observability.
 *
 * <p>Exposes counters that Prometheus scrapes and Grafana visualizes,
 * providing real-time insight into order flow health.</p>
 */
@Component
public class SagaMetrics {

    private final Counter ordersCreated;
    private final Counter sagaConfirmed;
    private final Counter sagaCancelled;
    private final Counter paymentSucceeded;
    private final Counter paymentFailed;
    private final Counter inventoryReserved;
    private final Counter inventoryInsufficient;

    public SagaMetrics(MeterRegistry registry) {
        ordersCreated = Counter.builder("nexus.orders.created")
                .description("Total orders created")
                .register(registry);

        sagaConfirmed = Counter.builder("nexus.saga.outcome")
                .tag("result", "confirmed")
                .description("Saga outcomes by result")
                .register(registry);

        sagaCancelled = Counter.builder("nexus.saga.outcome")
                .tag("result", "cancelled")
                .description("Saga outcomes by result")
                .register(registry);

        paymentSucceeded = Counter.builder("nexus.payment.result")
                .tag("result", "success")
                .description("Payment processing results")
                .register(registry);

        paymentFailed = Counter.builder("nexus.payment.result")
                .tag("result", "failure")
                .description("Payment processing results")
                .register(registry);

        inventoryReserved = Counter.builder("nexus.inventory.result")
                .tag("result", "reserved")
                .description("Inventory reservation results")
                .register(registry);

        inventoryInsufficient = Counter.builder("nexus.inventory.result")
                .tag("result", "insufficient")
                .description("Inventory reservation results")
                .register(registry);
    }

    public void recordOrderCreated() { ordersCreated.increment(); }
    public void recordSagaConfirmed() { sagaConfirmed.increment(); }
    public void recordSagaCancelled() { sagaCancelled.increment(); }
    public void recordPaymentSucceeded() { paymentSucceeded.increment(); }
    public void recordPaymentFailed() { paymentFailed.increment(); }
    public void recordInventoryReserved() { inventoryReserved.increment(); }
    public void recordInventoryInsufficient() { inventoryInsufficient.increment(); }
}
