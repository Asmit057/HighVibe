package com.springproject.lovable_clone.intelligence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.stream.Stream;

/**
 * AI settings (app.ai.* in config-service/config/intelligence-service.yaml).
 *
 * @param provider "gemini", "demo", or "auto" (Gemini when a Gemini API key is set, otherwise the offline demo)
 * @param historyMessages how many earlier chat messages are sent to the model as context
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        @DefaultValue("auto") String provider,
        @DefaultValue("12") int historyMessages,
        @DefaultValue Gemini gemini
) {
    public enum Provider { GEMINI, DEMO }

    /**
     * Google Gemini; free API keys from https://aistudio.google.com/apikey
     *
     * @param apiKey         the main key (GEMINI_API_KEY)
     * @param apiKeys        more keys (GEMINI_API_KEYS, comma-separated), used in turn and when a key fails
     * @param model          the main model (GEMINI_MODEL)
     * @param fallbackModels used in order while the main model is overloaded or missing (GEMINI_FALLBACK_MODELS)
     * @param maxAttempts    calls per generation before giving up, across keys and models
     */
    public record Gemini(
            String apiKey,
            List<String> apiKeys,
            @DefaultValue("gemini-3.5-flash-lite") String model,
            List<String> fallbackModels,
            @DefaultValue("32768") int maxOutputTokens,
            @DefaultValue("4") int maxAttempts
    ) {
        /** Every configured key, the main key first, without blanks or repeats. */
        public List<String> keys() {
            return merge(apiKey, apiKeys);
        }

        /** The main model, then the fallback models, without blanks or repeats. */
        public List<String> models() {
            return merge(model, fallbackModels);
        }

        private static List<String> merge(String first, List<String> more) {
            return Stream.concat(Stream.ofNullable(first), more == null ? Stream.empty() : more.stream())
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        }
    }

    public Provider resolveProvider() {
        return switch (provider == null ? "auto" : provider.trim().toLowerCase()) {
            case "gemini" -> Provider.GEMINI;
            case "demo" -> Provider.DEMO;
            default -> gemini.keys().isEmpty() ? Provider.DEMO : Provider.GEMINI;
        };
    }
}
