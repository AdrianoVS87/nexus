package com.nexus.inventory.service;

import com.nexus.inventory.config.GlobalExceptionHandler.ProductNotFoundException;
import com.nexus.inventory.domain.entity.Product;
import com.nexus.inventory.domain.entity.StockReservation;
import com.nexus.inventory.domain.entity.StockReservation.ReservationStatus;
import com.nexus.common.event.InventoryInsufficient;
import com.nexus.common.event.InventoryReserveRequested;
import com.nexus.common.event.InventoryReserved;
import com.nexus.common.event.OrderCancelled;
import com.nexus.inventory.dto.CreateProductRequest;
import com.nexus.inventory.dto.ProductResponse;
import com.nexus.inventory.dto.UpdateProductRequest;
import com.nexus.inventory.repository.ProductRepository;
import com.nexus.inventory.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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

        log.debug("Cache miss for all products, reading from PostgreSQL");
        var products = productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();

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
        try {
            var cached = redisTemplate.opsForValue().get(PRODUCT_CACHE_KEY + id);
            if (cached != null) {
                log.debug("Cache hit for product {}", id);
                return (ProductResponse) cached;
            }
        } catch (Exception e) {
            log.warn("Redis read failed for product {}: {}", id, e.getMessage());
        }

        log.debug("Cache miss for product {}, reading from PostgreSQL", id);
        var product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));

        var response = ProductResponse.from(product);
        syncProductToRedis(product);
        return response;
    }

    /**
     * Search products with optional filters. Results are always fresh from
     * PostgreSQL since search queries bypass the Redis cache.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ProductResponse> searchProducts(
            String name, java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice,
            Boolean inStock, org.springframework.data.domain.Pageable pageable) {
        return productRepository.search(name, minPrice, maxPrice, inStock, pageable)
                .map(ProductResponse::from);
    }

    // ── CRUD Write Operations ─────────────────────────────────────────

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        var product = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .stockQuantity(request.stockQuantity())
                .build();

        product = productRepository.save(product);
        syncProductToRedis(product);
        invalidateAllProductsCache();

        log.info("Product created: id={}, name={}", product.getId(), product.getName());
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request) {
        var product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));

        if (request.name() != null) product.setName(request.name());
        if (request.description() != null) product.setDescription(request.description());
        if (request.price() != null) product.setPrice(request.price());
        if (request.stockQuantity() != null) product.setStockQuantity(request.stockQuantity());

        product = productRepository.save(product);
        syncProductToRedis(product);
        invalidateAllProductsCache();

        log.info("Product updated: id={}, name={}", product.getId(), product.getName());
        return ProductResponse.from(product);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        var product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));
        productRepository.delete(product);
        try {
            redisTemplate.delete(PRODUCT_CACHE_KEY + id);
        } catch (Exception e) {
            log.warn("Failed to remove product {} from Redis: {}", id, e.getMessage());
        }
        invalidateAllProductsCache();
        log.info("Product deleted: id={}", id);
    }

    // ── Kafka Event Handlers ──────────────────────────────────────────

    @KafkaListener(topics = "inventory", groupId = "inventory-service")
    @Transactional
    public void handleInventoryEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        log.info("Inventory event received: type={}", event.getClass().getSimpleName());
        if (event instanceof InventoryReserveRequested irr) {
            handleInventoryReserveRequested(irr);
        }
    }

    @KafkaListener(topics = "orders", groupId = "inventory-service")
    @Transactional
    public void handleOrderEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        if (event instanceof OrderCancelled oc) {
            handleOrderCancelled(oc);
        }
    }

    private void handleInventoryReserveRequested(InventoryReserveRequested event) {
        log.info("Processing InventoryReserveRequested: orderId={}", event.orderId());

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

                var reservation = StockReservation.builder()
                        .productId(item.productId())
                        .orderId(event.orderId())
                        .quantity(item.quantity())
                        .build();
                reservationRepository.save(reservation);

                syncProductToRedis(product);
            }

            var reserved = new InventoryReserved(event.orderId(), Instant.now());
            kafkaTemplate.send("inventory", event.orderId().toString(), reserved);
            log.info("Inventory reserved for orderId={}", event.orderId());

            invalidateAllProductsCache();

        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock failure for orderId={}, retrying via Kafka", event.orderId());
            throw e;
        }
    }

    private void handleOrderCancelled(OrderCancelled event) {
        log.info("Processing OrderCancelled: orderId={}", event.orderId());

        var reservations = reservationRepository.findByOrderId(event.orderId());
        if (reservations.isEmpty()) {
            log.info("No reservations found for cancelled orderId={}", event.orderId());
            return;
        }

        for (var reservation : reservations) {
            if (reservation.getStatus() != ReservationStatus.RESERVED) {
                continue;
            }

            var product = productRepository.findById(reservation.getProductId())
                    .orElseThrow(() -> new IllegalStateException("Product not found: " + reservation.getProductId()));

            product.setStockQuantity(product.getStockQuantity() + reservation.getQuantity());
            productRepository.save(product);

            reservation.release();
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
    @Transactional(readOnly = true)
    public void syncAllProductsToRedis() {
        try {
            productRepository.findAll().forEach(this::syncProductToRedis);
            log.info("Synced all products to Redis on startup");
        } catch (Exception e) {
            log.warn("Failed to sync products to Redis on startup: {}", e.getMessage());
        }
    }
}
