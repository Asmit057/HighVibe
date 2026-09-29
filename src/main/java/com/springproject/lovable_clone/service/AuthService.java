package com.springproject.lovable_clone.service;

import com.springproject.lovable_clone.dto.auth.AuthResponse;
import com.springproject.lovable_clone.dto.auth.LoginRequest;
import com.springproject.lovable_clone.dto.auth.SignupRequest;

public interface AuthService {
     AuthResponse signup(SignupRequest request);

     AuthResponse login(LoginRequest request);
}
