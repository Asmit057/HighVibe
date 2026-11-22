package com.springproject.lovable_clone.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record GatewayProperties(
        @DefaultValue Security security,
        @DefaultValue Cors cors
) {
    public record Security(@DefaultValue List<String> publicRoutes) {}

    public record Cors(@DefaultValue List<String> allowedOrigins) {}
}
