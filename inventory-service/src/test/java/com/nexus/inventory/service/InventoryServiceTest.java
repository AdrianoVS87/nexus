package com.nexus.inventory.service;

import com.nexus.inventory.exception.ProductNotFoundException;
import com.nexus.inventory.domain.entity.Product;
import com.nexus.inventory.domain.entity.StockReservation;
import com.nexus.inventory.domain.entity.StockReservation.ReservationStatus;
import com.nexus.common.event.InventoryInsufficient;
import com.nexus.common.event.InventoryReserveRequested;
import com.nexus.common.event.InventoryReserveRequested.ReservationItem;
import com.nexus.common.event.InventoryReserved;
import com.nexus.common.event.OrderCancelled;
import com.nexus.inventory.dto.ProductResponse;
import com.nexus.inventory.repository.ProductRepository;
import com.nexus.inventory.repository.StockReservationRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockReservationRepository reservationRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ListOperations<String, Object> listOperations;

    @InjectMocks
    private InventoryService inventoryService;

    private Product sampleProduct;
    private UUID productId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        sampleProduct = Product.builder()
                .id(productId)
                .name("Mechanical Keyboard")
                .description("Cherry MX Brown")
                .price(BigDecimal.valueOf(149.99))
                .currency("USD")
                .stockQuantity(50)
                .version(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ── getAllProducts ─────────────────────────────────────────────────

    @Test
    @DisplayName("getAllProducts returns cached products when Redis has data")
    void getAllProducts_returnsCachedWhenAvailable() {
        var cached1 = new ProductResponse(productId, "KB", "desc", BigDecimal.TEN, "USD", 10);
        var cached2 = new ProductResponse(UUID.randomUUID(), "Mouse", "desc", BigDecimal.ONE, "USD", 5);

        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.range("products:all", 0, -1)).willReturn(List.of(cached1, cached2));

        List<ProductResponse> result = inventoryService.getAllProducts();

        assertThat(result).containsExactly(cached1, cached2);
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("getAllProducts falls back to PostgreSQL when Redis is empty")
    void getAllProducts_fallsBackToPostgreSQL() {
        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.range("products:all", 0, -1)).willReturn(List.of());
        given(productRepository.findAll()).willReturn(List.of(sampleProduct));
        given(redisTemplate.delete("products:all")).willReturn(true);
        given(redisTemplate.expire(eq("products:all"), any(Duration.class))).willReturn(true);

        List<ProductResponse> result = inventoryService.getAllProducts();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(productId);
        verify(productRepository).findAll();
    }

    // ── getProductById ────────────────────────────────────────────────

    @Test
    @DisplayName("getProductById returns cached product when available in Redis")
    void getProductById_returnsCachedProduct() {
        var cached = new ProductResponse(productId, "KB", "desc", BigDecimal.TEN, "USD", 10);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("product:" + productId)).willReturn(cached);

        ProductResponse result = inventoryService.getProductById(productId);

        assertThat(result).isEqualTo(cached);
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("getProductById throws ProductNotFoundException for non-existent product")
    void getProductById_throwsWhenNotFound() {
        var missingId = UUID.randomUUID();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("product:" + missingId)).willReturn(null);
        given(productRepository.findById(missingId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getProductById(missingId))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // ── handleInventoryReserveRequested ───────────────────────────────

    @Test
    @DisplayName("handleInventoryReserveRequested reserves stock and publishes InventoryReserved")
    void handleInventoryReserveRequested_reservesStockAndPublishesEvent() {
        var item = new ReservationItem(productId, 5);
        var event = new InventoryReserveRequested(orderId, List.of(item), Instant.now());
        var record = new ConsumerRecord<String, Object>("inventory", 0, 0, orderId.toString(), event);

        given(productRepository.findById(productId)).willReturn(Optional.of(sampleProduct));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        inventoryService.handleInventoryEvents(record);

        assertThat(sampleProduct.getStockQuantity()).isEqualTo(45);
        verify(productRepository).save(sampleProduct);
        verify(reservationRepository).save(any(StockReservation.class));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("inventory"), eq(orderId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(InventoryReserved.class);
    }

    @Test
    @DisplayName("handleInventoryReserveRequested publishes InventoryInsufficient when out of stock")
    void handleInventoryReserveRequested_publishesInsufficientWhenOutOfStock() {
        sampleProduct.setStockQuantity(2);
        var item = new ReservationItem(productId, 10);
        var event = new InventoryReserveRequested(orderId, List.of(item), Instant.now());
        var record = new ConsumerRecord<String, Object>("inventory", 0, 0, orderId.toString(), event);

        given(productRepository.findById(productId)).willReturn(Optional.of(sampleProduct));

        inventoryService.handleInventoryEvents(record);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("inventory"), eq(orderId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(InventoryInsufficient.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleInventoryReserveRequested re-throws ObjectOptimisticLockingFailureException for Kafka retry")
    void handleInventoryReserveRequested_retriesOnOptimisticLockFailure() {
        var item = new ReservationItem(productId, 5);
        var event = new InventoryReserveRequested(orderId, List.of(item), Instant.now());
        var record = new ConsumerRecord<String, Object>("inventory", 0, 0, orderId.toString(), event);

        given(productRepository.findById(productId)).willReturn(Optional.of(sampleProduct));
        given(productRepository.save(any())).willThrow(new ObjectOptimisticLockingFailureException("Product", null));

        assertThatThrownBy(() -> inventoryService.handleInventoryEvents(record))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ── handleOrderCancelled ──────────────────────────────────────────

    @Test
    @DisplayName("handleOrderCancelled releases reserved stock and transitions reservation to RELEASED")
    void handleOrderCancelled_releasesReservedStock() {
        var reservation = StockReservation.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .orderId(orderId)
                .quantity(5)
                .status(ReservationStatus.RESERVED)
                .createdAt(Instant.now())
                .build();

        var event = new OrderCancelled(orderId, UUID.randomUUID(), "Customer request", Instant.now());
        var record = new ConsumerRecord<String, Object>("orders", 0, 0, orderId.toString(), event);

        given(reservationRepository.findByOrderId(orderId)).willReturn(List.of(reservation));
        given(productRepository.findById(productId)).willReturn(Optional.of(sampleProduct));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        inventoryService.handleOrderEvents(record);

        assertThat(sampleProduct.getStockQuantity()).isEqualTo(55);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        verify(productRepository).save(sampleProduct);
        verify(reservationRepository).save(reservation);
    }

    @Test
    @DisplayName("handleOrderCancelled ignores non-RESERVED entries")
    void handleOrderCancelled_ignoresNonReservedEntries() {
        var released = StockReservation.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .orderId(orderId)
                .quantity(5)
                .status(ReservationStatus.RELEASED)
                .createdAt(Instant.now())
                .build();

        var consumed = StockReservation.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .orderId(orderId)
                .quantity(3)
                .status(ReservationStatus.CONSUMED)
                .createdAt(Instant.now())
                .build();

        var event = new OrderCancelled(orderId, UUID.randomUUID(), "Customer request", Instant.now());
        var record = new ConsumerRecord<String, Object>("orders", 0, 0, orderId.toString(), event);

        given(reservationRepository.findByOrderId(orderId)).willReturn(List.of(released, consumed));

        inventoryService.handleOrderEvents(record);

        assertThat(sampleProduct.getStockQuantity()).isEqualTo(50);
        verify(productRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
    }
}
