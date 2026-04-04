package com.nexus.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CorrelationIdFilter} verifying that correlation IDs
 * are generated when absent and preserved when already present.
 */
class CorrelationIdFilterTest {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void addsCorrelationId_whenMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String responseHeader = exchange.getResponse().getHeaders().getFirst(CORRELATION_ID_HEADER);
        assertThat(responseHeader).isNotNull().isNotBlank();
        // Verify it looks like a UUID
        assertThat(responseHeader).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void preservesCorrelationId_whenPresent() {
        String existingId = "existing-correlation-id-123";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders")
                .header(CORRELATION_ID_HEADER, existingId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String responseHeader = exchange.getResponse().getHeaders().getFirst(CORRELATION_ID_HEADER);
        assertThat(responseHeader).isEqualTo(existingId);
    }
}
