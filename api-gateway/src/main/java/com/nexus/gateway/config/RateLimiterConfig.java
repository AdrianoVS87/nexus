package com.nexus.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Configures rate limiting key resolution for Spring Cloud Gateway.
 */
@Configuration
public class RateLimiterConfig {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String UNKNOWN_CLIENT = "unknown";

    /**
     * Resolves the client IP address for rate limiting, respecting X-Forwarded-For
     * for requests arriving through reverse proxies or load balancers.
     *
     * @return a {@link KeyResolver} that extracts the originating client IP
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String forwardedFor = exchange.getRequest().getHeaders().getFirst(X_FORWARDED_FOR);
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                // X-Forwarded-For may contain a chain: "client, proxy1, proxy2"
                return Mono.just(forwardedFor.split(",")[0].trim());
            }
            if (exchange.getRequest().getRemoteAddress() != null) {
                return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
            }
            return Mono.just(UNKNOWN_CLIENT);
        };
    }
}
