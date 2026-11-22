package com.springproject.lovable_clone.intelligence.security;

import com.springproject.lovable_clone.common.security.HighVibeSecurity;
import com.springproject.lovable_clone.common.security.JsonSecurityErrorHandler;
import com.springproject.lovable_clone.common.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class IntelligenceSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthFilter jwtAuthFilter,
                                                   JsonSecurityErrorHandler errorHandler) throws Exception {
        return HighVibeSecurity.statelessJwtChain(http, jwtAuthFilter, errorHandler);
    }
}
