package com.springproject.lovable_clone.intelligence.llm;

import tools.jackson.databind.JsonNode;

import java.time.Duration;

/**
 * One failed Gemini call, sorted by what helps next. The message quotes Google's own error, never the key.
 */
final class GeminiFailure extends RuntimeException {

    enum Kind {
        /** this key is rate-limited, out of quota or rejected: rest it and use another key */
        KEY,
        /** the model is overloaded or the connection broke: use a fallback model, or try again after a short wait */
        BUSY,
        /** the model does not exist (or no longer): only another model can help */
        MODEL,
        /** the request itself is wrong (unknown model, bad input): trying again cannot help */
        FATAL
    }

    private static final Duration RATE_LIMIT_REST = Duration.ofMinutes(1);
    private static final Duration REJECTED_KEY_REST = Duration.ofMinutes(10);
    private static final Duration MAX_REST = Duration.ofMinutes(30);

    private final Kind kind;
    private final Duration wait;

    private GeminiFailure(Kind kind, String message, Duration wait) {
        super(message);
        this.kind = kind;
        this.wait = wait;
    }

    Kind kind() {
        return kind;
    }

    /** For KEY: how long the key rests. For BUSY: Google's suggested wait, or null. */
    Duration waitHint() {
        return wait;
    }

    /** Sorts an error object from the API, sent as an HTTP error body or as an event inside the stream. */
    static GeminiFailure fromError(int httpStatus, JsonNode error, String model) {
        int code = error.path("code").asInt(httpStatus);
        String status = error.path("status").asString("");
        String detail = error.path("message").asString("").strip().replaceAll("\\.+$", "");
        String what = code + (status.isEmpty() ? "" : " " + status) + (detail.isEmpty() ? "" : ": " + detail);
        Duration retryDelay = retryDelay(error);

        if (code == 429 || status.equals("RESOURCE_EXHAUSTED")) {
            return new GeminiFailure(Kind.KEY, "rate limit or quota reached (" + what + ")",
                    retryDelay == null ? RATE_LIMIT_REST : min(retryDelay, MAX_REST));
        }
        if (code == 401 || code == 403 || status.equals("UNAUTHENTICATED") || status.equals("PERMISSION_DENIED")
                || hasReason(error, "API_KEY_INVALID")) {
            return new GeminiFailure(Kind.KEY, "the API key was rejected (" + what + ")", REJECTED_KEY_REST);
        }
        if (code == 404) {
            return new GeminiFailure(Kind.MODEL, "model '" + model + "' was not found (" + what
                    + "). Set GEMINI_MODEL to a model listed in Google AI Studio", null);
        }
        if (code >= 500 || status.equals("UNAVAILABLE") || status.equals("INTERNAL") || status.equals("DEADLINE_EXCEEDED")) {
            return new GeminiFailure(Kind.BUSY, "Gemini is overloaded or failing (" + what + ")", retryDelay);
        }
        return new GeminiFailure(Kind.FATAL, "Gemini rejected the request (" + what + ")", null);
    }

    static GeminiFailure connection(String detail) {
        return new GeminiFailure(Kind.BUSY, "the connection to Gemini failed (" + detail + ")", null);
    }

    static GeminiFailure cutOff(int chunks) {
        return new GeminiFailure(Kind.BUSY,
                "Gemini closed the stream before the answer was complete (after " + chunks + " chunks)", null);
    }

    private static boolean hasReason(JsonNode error, String reason) {
        for (JsonNode detail : error.path("details")) {
            if (reason.equals(detail.path("reason").asString(""))) {
                return true;
            }
        }
        return false;
    }

    /** Google's RetryInfo detail, for example {"retryDelay": "37s"}. */
    private static Duration retryDelay(JsonNode error) {
        for (JsonNode detail : error.path("details")) {
            String delay = detail.path("retryDelay").asString("");
            if (delay.matches("\\d+(\\.\\d+)?s")) {
                return Duration.ofMillis(Math.round(Double.parseDouble(delay.substring(0, delay.length() - 1)) * 1000));
            }
        }
        return null;
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
