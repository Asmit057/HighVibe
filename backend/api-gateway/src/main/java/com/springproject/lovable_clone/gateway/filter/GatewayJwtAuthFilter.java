package com.springproject.lovable_clone.gateway.filter;

import com.springproject.lovable_clone.gateway.config.GatewayProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Rejects requests without a valid access token before they reach a service.
 * Services re-validate the same token, so the gateway is a first line of defence, not the only one.
 */
@Slf4j
@Component
public class GatewayJwtAuthFilter implements GlobalFilter, Ordered {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final GatewayProperties properties;
    private final SecretKey secretKey;

    public GatewayJwtAuthFilter(GatewayProperties properties, @Value("${jwt.secret-key}") String jwtSecretKey) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(jwtSecretKey.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (HttpMethod.OPTIONS.equals(request.getMethod()) || isPublic(path)) {
            return chain.filter(exchange);
        }

        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ") || header.substring(7).isBlank()) {
            return reject(exchange, "Missing or invalid Authorization header");
        }

        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(header.substring(7).trim());
        } catch (RuntimeException e) {
            log.debug("JWT rejected at gateway for {}: {}", path, e.getMessage());
            return reject(exchange, "Invalid or expired token");
        }
        return chain.filter(exchange);
    }

    private boolean isPublic(String path) {
        return properties.security().publicRoutes().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format("{\"status\":\"UNAUTHORIZED\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message, Instant.now());
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
