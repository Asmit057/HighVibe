package com.springproject.lovable_clone.account.service;

import com.springproject.lovable_clone.account.config.AccountProperties;
import com.springproject.lovable_clone.account.dto.AuthDtos.AuthResponse;
import com.springproject.lovable_clone.account.dto.AuthDtos.LoginRequest;
import com.springproject.lovable_clone.account.dto.AuthDtos.SignupRequest;
import com.springproject.lovable_clone.account.entity.User;
import com.springproject.lovable_clone.account.repository.UserRepository;
import com.springproject.lovable_clone.common.error.BadRequestException;
import com.springproject.lovable_clone.common.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final AuthUtil authUtil;
    private final RefreshTokenService refreshTokenService;
    private final AccountProperties properties;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = request.username().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(email)) {
            throw new BadRequestException("An account already exists for " + email);
        }
        User user = userRepository.save(User.builder()
                .username(email)
                .name(request.name().trim())
                .password(passwordEncoder.encode(request.password()))
                .build());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));
        return issueTokens((User) authentication.getPrincipal());
    }

    // keep the theft-detection revocation even when the refresh itself is rejected
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthResponse refresh(String refreshToken) {
        return issueTokens(refreshTokenService.consume(refreshToken));
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        }
    }

    private AuthResponse issueTokens(User user) {
        var ttl = properties.auth().accessTokenTtl();
        String accessToken = authUtil.generateAccessToken(user.getId(), user.getUsername(), user.getName(), ttl);
        String refreshToken = refreshTokenService.issue(user);
        return new AuthResponse(accessToken, refreshToken, ttl.toSeconds(), UserService.toProfile(user));
    }
}
