package com.springproject.lovable_clone.account.service;

import com.springproject.lovable_clone.account.config.AccountProperties;
import com.springproject.lovable_clone.account.entity.RefreshToken;
import com.springproject.lovable_clone.account.entity.User;
import com.springproject.lovable_clone.account.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Opaque, rotating refresh tokens. The raw value is returned to the client once; only its
 * SHA-256 hash is stored, so a database leak does not expose usable tokens.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountProperties properties;

    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(raw))
                .expiresAt(Instant.now().plus(properties.auth().refreshTokenTtl()))
                .build());
        return raw;
    }

    /** Validates a refresh token and revokes it; the caller issues a fresh pair. */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public User consume(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        Instant now = Instant.now();
        if (!token.isActive(now)) {
            if (token.getRevokedAt() != null) {
                // A revoked token being replayed suggests theft: end every session of this user.
                refreshTokenRepository.revokeAllForUser(token.getUser().getId(), now);
            }
            throw new BadCredentialsException("Refresh token expired or revoked");
        }
        token.setRevokedAt(now);
        return token.getUser();
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
