package com.nexus.gateway.config;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.test.StepVerifier;

/**
 * Unit tests for {@link RateLimiterConfig} verifying correct client IP extraction
 * from both direct connections and proxied requests.
 */
class RateLimiterConfigTest {

    private KeyResolver keyResolver;

    @BeforeEach
    void setUp() {
        keyResolver = new RateLimiterConfig().ipKeyResolver();
    }

    @Test
    void ipKeyResolver_extractsRemoteAddress() {
        InetSocketAddress remoteAddr = new InetSocketAddress(
                InetAddress.getLoopbackAddress(), 12345);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders")
                .remoteAddress(remoteAddr)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(keyResolver.resolve(exchange))
                .expectNext("127.0.0.1")
                .verifyComplete();
    }

    @Test
    void ipKeyResolver_prefersXForwardedFor() {
        InetSocketAddress remoteAddr = new InetSocketAddress(
                InetAddress.getLoopbackAddress(), 12345);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders")
                .remoteAddress(remoteAddr)
                .header("X-Forwarded-For", "203.0.113.50, 70.41.3.18")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(keyResolver.resolve(exchange))
                .expectNext("203.0.113.50")
                .verifyComplete();
    }

    @Test
    void ipKeyResolver_handlesNullAddress() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(keyResolver.resolve(exchange))
                .expectNext("unknown")
                .verifyComplete();
    }
}
