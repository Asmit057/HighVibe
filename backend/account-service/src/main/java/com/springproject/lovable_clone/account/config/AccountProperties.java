package com.springproject.lovable_clone.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "app")
public record AccountProperties(
        @DefaultValue("http://localhost:5173") String frontendUrl,
        @DefaultValue Auth auth,
        @DefaultValue Billing billing
) {
    public record Auth(
            @DefaultValue("30m") Duration accessTokenTtl,
            @DefaultValue("30d") Duration refreshTokenTtl
    ) {}

    public record Billing(
            String stripeSecretKey,
            String stripeWebhookSecret,
            @DefaultValue("true") boolean mockMode,
            @DefaultValue Map<String, String> stripePriceIds
    ) {
        public boolean stripeEnabled() {
            return stripeSecretKey != null && !stripeSecretKey.isBlank();
        }
    }
}
