package com.nexus.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.order.domain.entity.Order;
import com.nexus.order.domain.entity.OrderItem;
import com.nexus.order.domain.enums.OrderStatus;
import com.nexus.order.dto.CreateOrderRequest;
import com.nexus.order.dto.CreateOrderRequest.OrderItemRequest;
import com.nexus.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(
        partitions = 1,
        topics = {"orders", "payments", "inventory"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"}
)
@ActiveProfiles("test")
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    void createOrder_returns201WithOrderResponse() throws Exception {
        var request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new OrderItemRequest(UUID.randomUUID(), "Widget", 3, new BigDecimal("9.99")))
        );

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(request.userId().toString()))
                .andExpect(jsonPath("$.status").value("PAYMENT_REQUESTED"))
                .andExpect(jsonPath("$.totalAmount").value(29.97))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productName").value("Widget"))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
    }

    @Test
    void getOrder_returns200WithOrder() throws Exception {
        Order order = createAndSaveOrder();

        mockMvc.perform(get("/api/v1/orders/{id}", order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getOrder_returns404ForNonExistent() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order Not Found"));
    }

    @Test
    void getOrdersByUser_returnsPaginatedResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        createAndSaveOrderForUser(userId);
        createAndSaveOrderForUser(userId);

        mockMvc.perform(get("/api/v1/orders")
                        .param("userId", userId.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void createOrder_withInvalidInput_returns400() throws Exception {
        String invalidJson = """
                {
                    "userId": null,
                    "items": []
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"));
    }

    @Test
    void createOrder_withInvalidItemQuantity_returns400() throws Exception {
        String json = """
                {
                    "userId": "%s",
                    "items": [{
                        "productId": "%s",
                        "productName": "Test",
                        "quantity": 0,
                        "unitPrice": 10.00
                    }]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_withNegativeUnitPrice_returns400() throws Exception {
        String json = """
                {
                    "userId": "%s",
                    "items": [{
                        "productId": "%s",
                        "productName": "Test",
                        "quantity": 1,
                        "unitPrice": -5.00
                    }]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    private Order createAndSaveOrder() {
        return createAndSaveOrderForUser(UUID.randomUUID());
    }

    private Order createAndSaveOrderForUser(UUID userId) {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("25.00"))
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .productName("Test Product")
                .quantity(1)
                .unitPrice(new BigDecimal("25.00"))
                .subtotal(new BigDecimal("25.00"))
                .build();
        order.addItem(item);

        return orderRepository.save(order);
    }
}
