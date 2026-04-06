package com.nexus.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank String name,
        String description,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @NotNull @PositiveOrZero Integer stockQuantity
) {}
