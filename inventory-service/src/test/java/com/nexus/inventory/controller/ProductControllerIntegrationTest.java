package com.nexus.inventory.controller;

import com.nexus.inventory.config.GlobalExceptionHandler;
import com.nexus.inventory.config.GlobalExceptionHandler.ProductNotFoundException;
import com.nexus.inventory.dto.ProductResponse;
import com.nexus.inventory.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the ProductController HTTP layer.
 * Uses WebMvcTest to slice the context to the web layer only, avoiding
 * conflicts with the live services sharing PostgreSQL and Kafka.
 */
@WebMvcTest({ProductController.class, GlobalExceptionHandler.class})
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    @Test
    @DisplayName("GET /api/v1/products returns 200 with seeded data")
    void getAllProducts_returns200WithSeededData() throws Exception {
        var product1 = new ProductResponse(UUID.randomUUID(), "Test Keyboard", "desc", BigDecimal.valueOf(99.99), "USD", 100);
        var product2 = new ProductResponse(UUID.randomUUID(), "Wireless Mouse", "desc", BigDecimal.valueOf(79.99), "USD", 50);
        given(inventoryService.getAllProducts()).willReturn(List.of(product1, product2));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("Test Keyboard")))
                .andExpect(jsonPath("$[1].name", is("Wireless Mouse")));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} returns 200 for existing product")
    void getProductById_returns200() throws Exception {
        var productId = UUID.randomUUID();
        var product = new ProductResponse(productId, "Test Keyboard", "desc", BigDecimal.valueOf(99.99), "USD", 100);
        given(inventoryService.getProductById(productId)).willReturn(product);

        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(productId.toString())))
                .andExpect(jsonPath("$.name", is("Test Keyboard")))
                .andExpect(jsonPath("$.stockQuantity", is(100)));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} returns 404 for non-existent product")
    void getProductById_returns404ForNonExistent() throws Exception {
        var nonExistentId = UUID.randomUUID();
        given(inventoryService.getProductById(nonExistentId))
                .willThrow(new ProductNotFoundException("Product not found: " + nonExistentId));

        mockMvc.perform(get("/api/v1/products/{id}", nonExistentId))
                .andExpect(status().isNotFound());
    }
}
