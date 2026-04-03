package com.nexus.inventory.dto;

import com.nexus.inventory.domain.entity.Product;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        String currency,
        int stockQuantity
) implements Serializable {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCurrency(),
                product.getStockQuantity()
        );
    }
}
