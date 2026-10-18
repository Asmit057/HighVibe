package com.springproject.lovable_clone.controller;

import com.springproject.lovable_clone.dto.auth.AuthResponse;
import com.springproject.lovable_clone.dto.auth.LoginRequest;
import com.springproject.lovable_clone.dto.auth.SignupRequest;
import com.springproject.lovable_clone.dto.auth.UserProfileResponse;
import com.springproject.lovable_clone.security.AuthUtil;
import com.springproject.lovable_clone.service.AuthService;
import com.springproject.lovable_clone.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
// make all fields as private
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AuthController {
    private AuthService authService;
    private UserService userService;
    private AuthUtil authUtil;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request){
        return ResponseEntity.ok(authService.signup(request));
    }

    @PostMapping("/login")
    // in login request we just want email and password
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request)
    {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile(){
        Long userId = authUtil.getCurrentUserId();
        return ResponseEntity.ok(userService.getProfile(userId));
    }
}
