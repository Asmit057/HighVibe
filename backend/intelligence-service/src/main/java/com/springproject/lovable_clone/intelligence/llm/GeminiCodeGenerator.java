package com.springproject.lovable_clone.intelligence.llm;

import com.springproject.lovable_clone.intelligence.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Code generation with Google Gemini (free API keys from https://aistudio.google.com/apikey)
 * through the REST endpoint {@code models/{model}:streamGenerateContent}.
 *
 * The current project files are placed in the prompt, and the reply uses the tag protocol of
 * PromptUtils, which LlmResponseParser turns into chat events.
 *
 * A failed call is tried again, up to {@code maxAttempts} times: a rate-limited or rejected key rests and the
 * next key is used; an overloaded or missing model rests and the next fallback model is used (with a single
 * model, it is asked again after a short, growing wait). A resting key or model is used again once its rest is
 * over. Gemini can also report an error inside a stream that already sent text; that text is then discarded
 * through {@link ReplyListener#restart()}.
 */
@Slf4j
@Component
public class GeminiCodeGenerator implements CodeGenerator {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    // prompt budget for inlined file contents (Gemini models have a very large context window)
    private static final int MAX_FILE_CHARS = 400_000;
    private static final Duration MAX_PAUSE = Duration.ofSeconds(30);
    private static final Duration BUSY_MODEL_REST = Duration.ofMinutes(5);
    private static final Duration MISSING_MODEL_REST = Duration.ofHours(1);

    private final AiProperties properties;
    private final JsonMapper jsonMapper;
    private final String baseUrl;
    private final Duration backoff;
    private final FallbackPool keys;
    private final FallbackPool models;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Autowired
    public GeminiCodeGenerator(AiProperties properties, JsonMapper jsonMapper) {
        this(properties, jsonMapper, BASE_URL, Duration.ofSeconds(2), Clock.systemUTC());
    }

    GeminiCodeGenerator(AiProperties properties, JsonMapper jsonMapper, String baseUrl, Duration backoff, Clock clock) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.baseUrl = baseUrl;
        this.backoff = backoff;
        this.keys = new FallbackPool(properties.gemini().keys(), FallbackPool.Order.IN_TURN, clock);
        this.models = new FallbackPool(properties.gemini().models(), FallbackPool.Order.IN_ORDER, clock);
    }

    @Override
    public GenerationResult generate(GenerationRequest request, ReplyListener listener) {
        AiProperties.Gemini gemini = properties.gemini();
        if (keys.size() == 0) {
            throw new IllegalStateException("No Gemini API key is set (GEMINI_API_KEY or GEMINI_API_KEYS)");
        }
        if (models.size() == 0) {
            throw new IllegalStateException("No Gemini model is set (GEMINI_MODEL)");
        }
        String body = requestBody(request,
                PromptUtils.userTurn(request.files(), request.readFile(), request.userMessage(), MAX_FILE_CHARS),
                gemini.maxOutputTokens());

        int attempts = Math.max(1, gemini.maxAttempts());
        GeminiFailure last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            FallbackPool.Entry key = keys.take().orElse(null);
            if (key == null) {
                throw new IllegalStateException(allKeysResting(last));
            }
            // an overloaded model is not waited for: when every model rests, the one free soonest is used
            FallbackPool.Entry model = models.take().orElseGet(models::soonest);
            StringBuilder fullText = new StringBuilder();
            try {
                StreamState state = stream(body, model.value(), key.value(), fullText, listener::delta);
                if (keys.succeeded(key)) {
                    log.info("Gemini key {} of {} works again", key.number(), keys.size());
                }
                if (models.succeeded(model)) {
                    log.info("Gemini model {} works again", model.value());
                }
                String note = closingNote(state.finishReason);
                if (note != null) {
                    emit(fullText, listener::delta, "\n<message phase=\"completed\">" + note + "</message>");
                }
                long output = Math.max(0, state.totalTokens - state.promptTokens);
                return new GenerationResult(fullText.toString(), state.promptTokens, output, model.value());
            } catch (GeminiFailure failure) {
                last = failure;
                boolean anotherModel = models.size() > 1;
                boolean retry = attempt < attempts && switch (failure.kind()) {
                    case KEY, BUSY -> true;
                    case MODEL -> anotherModel;
                    case FATAL -> false;
                };
                String next = retry ? "trying again" : "giving up";
                if (failure.kind() == GeminiFailure.Kind.KEY) {
                    keys.rest(key, failure.waitHint());
                    next += ", key " + key.number() + " rests " + failure.waitHint().toSeconds() + " s";
                } else if (anotherModel && failure.kind() != GeminiFailure.Kind.FATAL) {
                    Duration rest = failure.kind() == GeminiFailure.Kind.MODEL ? MISSING_MODEL_REST : BUSY_MODEL_REST;
                    models.rest(model, rest);
                    next += ", model " + model.value() + " rests " + rest.toMinutes() + " min";
                }
                log.warn("Gemini attempt {}/{} for project {} with model {} and key {} of {} failed: {}; {}",
                        attempt, attempts, request.projectId(), model.value(), key.number(), keys.size(),
                        failure.getMessage(), next);
                if (!retry) {
                    break;
                }
                if (!fullText.isEmpty()) {
                    listener.restart();
                }
                // with no other model free, an overloaded model is asked again after a short, growing wait
                if (failure.kind() == GeminiFailure.Kind.BUSY && (!anotherModel || !models.hasFree())) {
                    Duration wait = failure.waitHint() != null ? failure.waitHint() : backoff.multipliedBy(1L << (attempt - 1));
                    pause(wait.compareTo(MAX_PAUSE) > 0 ? MAX_PAUSE : wait);
                }
            }
        }
        throw new IllegalStateException(giveUpMessage(last, attempts));
    }

    /** One call: streams the answer into fullText, or throws a GeminiFailure saying what went wrong. */
    private StreamState stream(String body, String model, String apiKey, StringBuilder fullText, Consumer<String> onDelta) {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl
                        + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":streamGenerateContent?alt=sse"))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<Stream<String>> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
        } catch (IOException e) {
            throw GeminiFailure.connection(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling Gemini");
        }

        if (response.statusCode() != 200) {
            String text = String.join("\n", response.body().toList());
            throw GeminiFailure.fromError(response.statusCode(), errorObject(text), model);
        }

        StreamState state = new StreamState();
        StringBuilder nonSse = new StringBuilder();
        try (Stream<String> lines = response.body()) {
            lines.forEach(line -> {
                if (line.startsWith("data:")) {
                    handleChunk(jsonMapper.readTree(line.substring(5).trim()), fullText, onDelta, state);
                } else {
                    nonSse.append(line).append('\n');
                }
            });
        } catch (UncheckedIOException e) {
            throw GeminiFailure.connection(e.getMessage());
        }
        // without SSE framing the endpoint returns one JSON array of chunks
        if (state.chunks == 0 && !nonSse.isEmpty()) {
            JsonNode array = jsonMapper.readTree(nonSse.toString());
            for (JsonNode chunk : array.isArray() ? array : List.of(array)) {
                handleChunk(chunk, fullText, onDelta, state);
            }
        }
        if (state.error != null) {
            throw GeminiFailure.fromError(200, state.error, model);
        }
        if (state.finishReason == null) {
            throw GeminiFailure.cutOff(state.chunks);
        }
        return state;
    }

    /**
     * The note shown after a reply that did not end normally, or null when Gemini finished on its own.
     * The unfinished file of a reply that hit the output limit is not saved.
     */
    static String closingNote(String finishReason) {
        return switch (finishReason) {
            case "STOP" -> null;
            case "MAX_TOKENS" -> "The reply hit the output limit. Ask me to continue and I will finish the remaining files.";
            default -> "Gemini stopped early (" + finishReason + "). Try rephrasing the request.";
        };
    }

    /** Builds the generateContent request: system instruction, alternating turns, output limit. */
    String requestBody(GenerationRequest request, String userTurn, int maxOutputTokens) {
        ObjectNode body = jsonMapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject()
                .put("text", PromptUtils.CODE_GENERATION_SYSTEM_PROMPT);
        ArrayNode contents = body.putArray("contents");
        for (HistoryTurn turn : Conversation.alternating(request.history(), userTurn)) {
            ObjectNode content = contents.addObject();
            content.put("role", turn.fromUser() ? "user" : "model");
            content.putArray("parts").addObject().put("text", turn.text());
        }
        body.putObject("generationConfig").put("maxOutputTokens", maxOutputTokens);
        return jsonMapper.writeValueAsString(body);
    }

    /** Reads one streamed chunk: text parts, finish reason, token usage, or an error Gemini sends mid-stream. */
    static void handleChunk(JsonNode chunk, StringBuilder fullText, Consumer<String> onDelta, StreamState state) {
        state.chunks++;
        if (chunk.path("error").isObject()) {
            state.error = chunk.path("error");
            return;
        }
        JsonNode candidate = chunk.path("candidates").path(0);
        for (JsonNode part : candidate.path("content").path("parts")) {
            // skip thought summaries; only the answer text is shown and parsed
            if (part.path("thought").asBoolean(false)) {
                continue;
            }
            String text = part.path("text").asString("");
            if (!text.isEmpty()) {
                fullText.append(text);
                onDelta.accept(text);
            }
        }
        if (candidate.hasNonNull("finishReason")) {
            state.finishReason = candidate.get("finishReason").asString();
        }
        JsonNode usage = chunk.path("usageMetadata");
        if (!usage.isMissingNode()) {
            state.promptTokens = usage.path("promptTokenCount").asLong(state.promptTokens);
            state.totalTokens = usage.path("totalTokenCount").asLong(state.totalTokens);
        }
    }

    /** The "error" object of an error body; plain text becomes its message. */
    private JsonNode errorObject(String body) {
        try {
            JsonNode error = jsonMapper.readTree(body).path("error");
            if (error.isObject()) {
                return error;
            }
        } catch (RuntimeException ignored) {
            // not JSON: keep the raw body as the message
        }
        return jsonMapper.createObjectNode().put("message", body);
    }

    private String giveUpMessage(GeminiFailure last, int attempts) {
        return switch (last.kind()) {
            case BUSY -> last.getMessage() + ". Tried " + attempts + " times with " + String.join(", ", models.values())
                    + "; try again in a minute, or add models to GEMINI_FALLBACK_MODELS";
            case KEY -> keys.size() == 1
                    ? last.getMessage() + ". Add more keys to GEMINI_API_KEYS or try again later"
                    : "every Gemini API key failed (last: " + last.getMessage() + "). Try again later";
            case MODEL, FATAL -> last.getMessage();
        };
    }

    private String allKeysResting(GeminiFailure last) {
        return "all " + keys.size() + " Gemini API key(s) are resting after errors"
                + (last != null ? " (last: " + last.getMessage() + ")" : "")
                + "; the first is free again in " + Math.max(1, keys.nextFreeIn().toSeconds()) + " s";
    }

    private static void pause(Duration wait) {
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry Gemini");
        }
    }

    private static void emit(StringBuilder fullText, Consumer<String> onDelta, String text) {
        fullText.append(text);
        onDelta.accept(text);
    }

    static final class StreamState {
        int chunks;
        String finishReason;
        JsonNode error;
        long promptTokens;
        long totalTokens;
    }
}
