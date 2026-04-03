package com.nexus.inventory.service;

import com.nexus.inventory.domain.entity.Product;
import com.nexus.inventory.domain.entity.StockReservation;
import com.nexus.inventory.domain.event.InventoryInsufficient;
import com.nexus.inventory.domain.event.InventoryReserveRequested;
import com.nexus.inventory.domain.event.InventoryReserved;
import com.nexus.inventory.dto.ProductResponse;
import com.nexus.inventory.repository.ProductRepository;
import com.nexus.inventory.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final ProductRepository productRepository;
    private final StockReservationRepository reservationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PRODUCT_CACHE_KEY = "product:";

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

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

        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock failure for orderId={}, retrying is handled by Kafka", event.orderId());
            throw e; // Will cause Kafka retry
        }
    }

    private void publishInsufficient(InventoryReserveRequested event, java.util.UUID productId, String reason) {
        var insufficient = new InventoryInsufficient(event.orderId(), productId, reason, Instant.now());
        kafkaTemplate.send("inventory", event.orderId().toString(), insufficient);
        log.info("Inventory insufficient for orderId={}: {}", event.orderId(), reason);
    }

    public void syncProductToRedis(Product product) {
        var response = ProductResponse.from(product);
        redisTemplate.opsForValue().set(PRODUCT_CACHE_KEY + product.getId(), response);
    }

    public void syncAllProductsToRedis() {
        productRepository.findAll().forEach(this::syncProductToRedis);
        log.info("Synced all products to Redis");
    }
}
