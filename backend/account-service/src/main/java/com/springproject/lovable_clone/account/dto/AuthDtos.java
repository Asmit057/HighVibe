package com.springproject.lovable_clone.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies of the /api/auth endpoints.
 */
public final class AuthDtos {

    private AuthDtos() {}

    public record SignupRequest(
            @Email @NotBlank String username,
            @NotBlank @Size(min = 1, max = 60) String name,
            @NotBlank @Size(min = 6, max = 100) String password
    ) {}

    public record LoginRequest(
            @Email @NotBlank String username,
            @NotBlank String password
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record UserProfileResponse(Long id, String username, String name) {}

    public record AuthResponse(
            String token,
            String refreshToken,
            long expiresIn,
            UserProfileResponse user
    ) {}
}
