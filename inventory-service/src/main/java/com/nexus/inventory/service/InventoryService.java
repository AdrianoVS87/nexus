package com.nexus.inventory.service;

import com.nexus.inventory.config.GlobalExceptionHandler.ProductNotFoundException;
import com.nexus.inventory.domain.entity.Product;
import com.nexus.inventory.domain.entity.StockReservation;
import com.nexus.inventory.domain.entity.StockReservation.ReservationStatus;
import com.nexus.inventory.domain.event.InventoryInsufficient;
import com.nexus.inventory.domain.event.InventoryReserveRequested;
import com.nexus.inventory.domain.event.InventoryReserved;
import com.nexus.inventory.domain.event.OrderCancelled;
import com.nexus.inventory.dto.ProductResponse;
import com.nexus.inventory.repository.ProductRepository;
import com.nexus.inventory.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final ProductRepository productRepository;
    private final StockReservationRepository reservationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PRODUCT_CACHE_KEY = "product:";
    private static final String ALL_PRODUCTS_CACHE_KEY = "products:all";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    // ── CQRS Read Operations ──────────────────────────────────────────

    public List<ProductResponse> getAllProducts() {
        // Try Redis first (cache-aside)
        try {
            var cached = redisTemplate.opsForList().range(ALL_PRODUCTS_CACHE_KEY, 0, -1);
            if (cached != null && !cached.isEmpty()) {
                log.debug("Cache hit for all products");
                return cached.stream()
                        .map(obj -> (ProductResponse) obj)
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Redis read failed, falling back to PostgreSQL: {}", e.getMessage());
        }

        // Fall back to PostgreSQL
        log.debug("Cache miss for all products, reading from PostgreSQL");
        var products = productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();

        // Populate cache
        try {
            redisTemplate.delete(ALL_PRODUCTS_CACHE_KEY);
            if (!products.isEmpty()) {
                redisTemplate.opsForList().rightPushAll(ALL_PRODUCTS_CACHE_KEY, products.toArray());
                redisTemplate.expire(ALL_PRODUCTS_CACHE_KEY, CACHE_TTL);
            }
        } catch (Exception e) {
            log.warn("Failed to populate Redis cache: {}", e.getMessage());
        }

        return products;
    }

    public ProductResponse getProductById(UUID id) {
        // Try Redis first
        try {
            var cached = redisTemplate.opsForValue().get(PRODUCT_CACHE_KEY + id);
            if (cached != null) {
                log.debug("Cache hit for product {}", id);
                return (ProductResponse) cached;
            }
        } catch (Exception e) {
            log.warn("Redis read failed for product {}, falling back to PostgreSQL: {}", id, e.getMessage());
        }

        // Fall back to PostgreSQL
        log.debug("Cache miss for product {}, reading from PostgreSQL", id);
        var product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));

        var response = ProductResponse.from(product);
        syncProductToRedis(product);
        return response;
    }

    // ── Kafka Event Handlers ──────────────────────────────────────────

    @KafkaListener(topics = "inventory", groupId = "inventory-service")
    @Transactional
    public void handleInventoryRequest(InventoryReserveRequested event) {
        log.info("Received InventoryReserveRequested: orderId={}", event.orderId());

        try {
            for (var item : event.items()) {
                var product = productRepository.findById(item.productId())
                        .orElseThrow(() -> new IllegalStateException("Product not found: " + item.productId()));

                if (!product.hasStock(item.quantity())) {
                    publishInsufficient(event, item.productId(),
                            "Insufficient stock: requested=" + item.quantity() + ", available=" + product.getStockQuantity());
                    return;
                }

                product.reserveStock(item.quantity());
                productRepository.save(product);

                // Create reservation record
                var reservation = StockReservation.builder()
                        .productId(item.productId())
                        .orderId(event.orderId())
                        .quantity(item.quantity())
                        .build();
                reservationRepository.save(reservation);

                // Update Redis read model
                syncProductToRedis(product);
            }

            var reserved = new InventoryReserved(event.orderId(), Instant.now());
            kafkaTemplate.send("inventory", event.orderId().toString(), reserved);
            log.info("Inventory reserved for orderId={}", event.orderId());

            invalidateAllProductsCache();

        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock failure for orderId={}, retrying is handled by Kafka", event.orderId());
            throw e; // Will cause Kafka retry
        }
    }

    @KafkaListener(topics = "orders", groupId = "inventory-service")
    @Transactional
    public void handleOrderCancelled(OrderCancelled event) {
        log.info("Received OrderCancelled: orderId={}", event.orderId());

        var reservations = reservationRepository.findByOrderId(event.orderId());
        if (reservations.isEmpty()) {
            log.info("No reservations found for cancelled orderId={}", event.orderId());
            return;
        }

        for (var reservation : reservations) {
            if (reservation.getStatus() != ReservationStatus.RESERVED) {
                log.debug("Skipping reservation {} with status {}", reservation.getId(), reservation.getStatus());
                continue;
            }

            var product = productRepository.findById(reservation.getProductId())
                    .orElseThrow(() -> new IllegalStateException("Product not found: " + reservation.getProductId()));

            product.setStockQuantity(product.getStockQuantity() + reservation.getQuantity());
            productRepository.save(product);

            reservation.setStatus(ReservationStatus.RELEASED);
            reservationRepository.save(reservation);

            syncProductToRedis(product);

            log.info("Released {} units of product {} for orderId={}",
                    reservation.getQuantity(), reservation.getProductId(), event.orderId());
        }

        invalidateAllProductsCache();
    }

    // ── Cache Management ──────────────────────────────────────────────

    private void publishInsufficient(InventoryReserveRequested event, UUID productId, String reason) {
        var insufficient = new InventoryInsufficient(event.orderId(), productId, reason, Instant.now());
        kafkaTemplate.send("inventory", event.orderId().toString(), insufficient);
        log.info("Inventory insufficient for orderId={}: {}", event.orderId(), reason);
    }

    public void syncProductToRedis(Product product) {
        try {
            var response = ProductResponse.from(product);
            redisTemplate.opsForValue().set(PRODUCT_CACHE_KEY + product.getId(), response, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to sync product {} to Redis: {}", product.getId(), e.getMessage());
        }
    }

    private void invalidateAllProductsCache() {
        try {
            redisTemplate.delete(ALL_PRODUCTS_CACHE_KEY);
        } catch (Exception e) {
            log.warn("Failed to invalidate all-products cache: {}", e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncAllProductsToRedis() {
        try {
            productRepository.findAll().forEach(this::syncProductToRedis);
            log.info("Synced all products to Redis on startup");
        } catch (Exception e) {
            log.warn("Failed to sync products to Redis on startup: {}", e.getMessage());
        }
    }
}
