package com.springproject.lovable_clone.intelligence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI settings (app.ai.* in config-service/config/intelligence-service.yaml).
 *
 * @param provider "gemini", "demo", or "auto" (Gemini when GEMINI_API_KEY is set, otherwise the offline demo)
 * @param historyMessages how many earlier chat messages are sent to the model as context
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        @DefaultValue("auto") String provider,
        @DefaultValue("12") int historyMessages,
        @DefaultValue Gemini gemini
) {
    public enum Provider { GEMINI, DEMO }

    /** Google Gemini; free API keys from https://aistudio.google.com/apikey */
    public record Gemini(
            String apiKey,
            @DefaultValue("gemini-3.5-flash-lite") String model,
            @DefaultValue("32768") int maxOutputTokens
    ) {}

    public Provider resolveProvider() {
        return switch (provider == null ? "auto" : provider.trim().toLowerCase()) {
            case "gemini" -> Provider.GEMINI;
            case "demo" -> Provider.DEMO;
            default -> gemini.apiKey() != null && !gemini.apiKey().isBlank() ? Provider.GEMINI : Provider.DEMO;
        };
    }
}
