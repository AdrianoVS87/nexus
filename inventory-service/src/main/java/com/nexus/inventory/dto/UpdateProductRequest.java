package com.nexus.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record UpdateProductRequest(
        String name,
        String description,
        @DecimalMin("0.01") BigDecimal price,
        @PositiveOrZero Integer stockQuantity
) {}
