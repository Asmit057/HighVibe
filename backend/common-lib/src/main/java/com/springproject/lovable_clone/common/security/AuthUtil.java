package com.springproject.lovable_clone.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;

/**
 * Issues and verifies HS256 access tokens. Every service shares the same secret, so any
 * service can authenticate a request without calling account-service.
 */
public class AuthUtil {

    private final SecretKey secretKey;

    public AuthUtil(String jwtSecretKey) {
        if (jwtSecretKey == null || jwtSecretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret-key must be set and at least 32 bytes long");
        }
        this.secretKey = Keys.hmacShaKeyFor(jwtSecretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String username, String name, Duration ttl) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId.toString())
                .claim("name", name)
                .claim("type", "access")
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttl.toMillis()))
                .signWith(secretKey)
                .compact();
    }

    /** Parses and validates the token; throws io.jsonwebtoken.JwtException when invalid or expired. */
    public JwtUserPrincipal verifyAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = Long.parseLong(claims.get("userId", String.class));
        return new JwtUserPrincipal(userId, claims.get("name", String.class), claims.getSubject(), List.of());
    }

    public JwtUserPrincipal getCurrentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("No JWT found");
        }
        return principal;
    }

    public Long getCurrentUserId() {
        return getCurrentPrincipal().userId();
    }
}
