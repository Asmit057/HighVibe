package com.springproject.lovable_clone.common.security;

import org.springframework.security.core.GrantedAuthority;

import java.util.List;

/**
 * The authenticated caller, rebuilt from the JWT on every request.
 */
public record JwtUserPrincipal(
        Long userId,
        String name,
        String username,
        List<GrantedAuthority> authorities
) {
}
