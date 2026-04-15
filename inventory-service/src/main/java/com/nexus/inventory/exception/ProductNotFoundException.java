package com.nexus.inventory.exception;

/**
 * Thrown when a requested product does not exist in the catalog.
 */
public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String message) {
        super(message);
    }
}
