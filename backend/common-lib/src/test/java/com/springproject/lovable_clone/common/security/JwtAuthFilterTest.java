package com.springproject.lovable_clone.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthFilterTest {

    private static final String SECRET = "highvibe-test-secret-0123456789-0123456789-0123456789";

    private final AuthUtil authUtil = new AuthUtil(SECRET);
    private final JwtAuthFilter filter = new JwtAuthFilter(authUtil);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Authentication filterWith(String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        if (header != null) {
            request.addHeader("Authorization", header);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).as("the chain always continues").isNotNull();
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void validToken_authenticatesTheUser() throws Exception {
        String token = authUtil.generateAccessToken(42L, "ada@example.com", "Ada", Duration.ofMinutes(5));

        Authentication auth = filterWith("Bearer " + token);

        assertThat(auth).isNotNull();
        JwtUserPrincipal principal = (JwtUserPrincipal) auth.getPrincipal();
        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.username()).isEqualTo("ada@example.com");
        assertThat(principal.name()).isEqualTo("Ada");
        assertThat(auth.getCredentials()).isEqualTo(token);
    }

    @Test
    void missingHeader_staysAnonymous() throws Exception {
        assertThat(filterWith(null)).isNull();
    }

    @Test
    void malformedOrEmptyToken_staysAnonymous() throws Exception {
        assertThat(filterWith("Bearer not-a-jwt")).isNull();
        assertThat(filterWith("Bearer ")).isNull();
        assertThat(filterWith("Basic abc")).isNull();
    }

    @Test
    void expiredToken_staysAnonymous() throws Exception {
        String expired = Jwts.builder()
                .subject("ada@example.com")
                .claim("userId", "42")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThat(filterWith("Bearer " + expired)).isNull();
    }

    @Test
    void tokenSignedWithAnotherKey_staysAnonymous() throws Exception {
        AuthUtil other = new AuthUtil("a-completely-different-secret-0123456789-0123456789");
        String forged = other.generateAccessToken(1L, "eve@example.com", "Eve", Duration.ofMinutes(5));
        assertThat(filterWith("Bearer " + forged)).isNull();
    }

    @Test
    void shortSecret_isRejectedAtStartup() {
        assertThatThrownBy(() -> new AuthUtil("too-short")).isInstanceOf(IllegalStateException.class);
    }
}
