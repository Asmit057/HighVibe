package com.springproject.lovable_clone.service.impl;

import com.springproject.lovable_clone.dto.auth.AuthResponse;
import com.springproject.lovable_clone.dto.auth.LoginRequest;
import com.springproject.lovable_clone.dto.auth.SignupRequest;
import com.springproject.lovable_clone.entity.User;
import com.springproject.lovable_clone.error.BadRequestException;
import com.springproject.lovable_clone.mapper.UserMapper;
import com.springproject.lovable_clone.repository.UserRepository;
import com.springproject.lovable_clone.security.AuthUtil;
import com.springproject.lovable_clone.service.AuthService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// to create the bin of authservice
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AuthServiceImpl implements AuthService {

    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    AuthUtil authUtil;
    AuthenticationManager authenticationManager;
    @Override
    public AuthResponse signup(SignupRequest request) {

        System.out.println("USERNAME = " + request.username());
        System.out.println("NAME = " + request.name());
        System.out.println("PASSWORD = " + request.password());

        userRepository.findByUsername(request.username()).ifPresent(user -> {
            throw new BadRequestException("User already exists with username: " + request.username());
        });
        User user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encode(request.password()));
        user = userRepository.save(user);

        String token = authUtil.generateAccessToken(user);
 return new AuthResponse(token , userMapper.toUserProfileResponse(user));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.username(),request.password()));
        
        User user  = (User)authentication.getPrincipal();
        String token = authUtil.generateAccessToken(user);
        return new AuthResponse(token, userMapper.toUserProfileResponse(user));

    }
}
