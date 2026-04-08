package com.nexus.order.integration;

import com.nexus.common.event.InventoryReserved;
import com.nexus.common.event.PaymentCompleted;
import com.nexus.common.event.PaymentFailed;
import com.nexus.order.domain.enums.OrderStatus;
import com.nexus.order.dto.CreateOrderRequest;
import com.nexus.order.dto.CreateOrderRequest.OrderItemRequest;
import com.nexus.order.repository.OrderRepository;
import com.nexus.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test that exercises the saga flow using the real Spring context,
 * embedded Kafka, and H2 database.
 *
 * <p>Simulates external service responses by publishing events directly to
 * Kafka topics that the OrderSagaListener consumes.</p>
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"orders", "payments", "inventory"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"}
)
@ActiveProfiles("test")
@DisplayName("Saga flow integration")
class SagaFlowIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    private UUID createTestOrder() {
        var request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new OrderItemRequest(
                        UUID.randomUUID(), "Test Product", 1, new BigDecimal("99.99")))
        );
        var response = orderService.createOrder(request);
        return response.id();
    }

    @Test
    @DisplayName("Happy path: PAYMENT_REQUESTED → PaymentCompleted → InventoryReserved → CONFIRMED")
    void happyPath_orderConfirmed() {
        UUID orderId = createTestOrder();

        // Verify initial state
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAYMENT_REQUESTED);

        // Simulate payment service response
        var paymentCompleted = new PaymentCompleted(UUID.randomUUID(), orderId, new BigDecimal("99.99"), Instant.now());
        kafkaTemplate.send("payments", orderId.toString(), paymentCompleted);

        // Wait for saga to transition through PAYMENT_COMPLETED → INVENTORY_REQUESTED
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                        .isEqualTo(OrderStatus.INVENTORY_REQUESTED));

        // Simulate inventory service response
        var inventoryReserved = new InventoryReserved(orderId, Instant.now());
        kafkaTemplate.send("inventory", orderId.toString(), inventoryReserved);

        // Wait for saga to reach CONFIRMED
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                        .isEqualTo(OrderStatus.CONFIRMED));
    }

    @Test
    @DisplayName("Compensation: PaymentFailed → CANCELLED")
    void compensationPath_paymentFailed() {
        UUID orderId = createTestOrder();

        // Simulate payment failure
        var paymentFailed = new PaymentFailed(orderId, UUID.randomUUID(), "Insufficient funds", Instant.now());
        kafkaTemplate.send("payments", orderId.toString(), paymentFailed);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                        .isEqualTo(OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("Replay safety: duplicate PaymentCompleted does not corrupt state")
    void replaySafety_duplicateEventsIgnored() {
        UUID orderId = createTestOrder();

        var paymentCompleted = new PaymentCompleted(UUID.randomUUID(), orderId, new BigDecimal("99.99"), Instant.now());
        kafkaTemplate.send("payments", orderId.toString(), paymentCompleted);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                        .isEqualTo(OrderStatus.INVENTORY_REQUESTED));

        // Simulate inventory reserved → CONFIRMED
        kafkaTemplate.send("inventory", orderId.toString(), new InventoryReserved(orderId, Instant.now()));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                        .isEqualTo(OrderStatus.CONFIRMED));

        // Send duplicate PaymentCompleted — should be ignored, order stays CONFIRMED
        kafkaTemplate.send("payments", orderId.toString(), paymentCompleted);

        // Wait a bit and verify state didn't change
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }
}
