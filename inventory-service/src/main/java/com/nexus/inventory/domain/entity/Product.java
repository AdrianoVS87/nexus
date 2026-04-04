package com.nexus.inventory.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private String currency;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (currency == null) currency = "USD";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Checks whether the requested quantity is available in stock.
     *
     * @param quantity the number of units to check — must be positive
     * @return true if stock covers the requested quantity
     */
    public boolean hasStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive, got: " + quantity);
        }
        return stockQuantity >= quantity;
    }

    /**
     * Reserves the given quantity by decrementing stock.
     *
     * @param quantity the number of units to reserve — must be positive
     * @throws IllegalArgumentException if quantity is not positive
     * @throws IllegalStateException    if insufficient stock is available
     */
    public void reserveStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive, got: " + quantity);
        }
        if (!hasStock(quantity)) {
            throw new IllegalStateException("Insufficient stock for product: " + id);
        }
        stockQuantity -= quantity;
    }
}
