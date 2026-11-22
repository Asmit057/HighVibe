package com.springproject.lovable_clone.intelligence.llm;

import com.springproject.lovable_clone.intelligence.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiCodeGenerator implements CodeGenerator {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    // prompt budget for inlined file contents (Gemini models have a very large context window)
    private static final int MAX_FILE_CHARS = 400_000;

    private final AiProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Override
    public GenerationResult generate(GenerationRequest request, Consumer<String> onDelta) {
        AiProperties.Gemini gemini = properties.gemini();
        if (gemini.apiKey() == null || gemini.apiKey().isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not set");
        }

        String userTurn = PromptUtils.userTurn(request.files(), request.readFile(),
                request.userMessage(), MAX_FILE_CHARS);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(BASE_URL
                        + URLEncoder.encode(gemini.model(), StandardCharsets.UTF_8) + ":streamGenerateContent?alt=sse"))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", gemini.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(request, userTurn, gemini.maxOutputTokens())))
                .build();

        HttpResponse<Stream<String>> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach the Gemini API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling Gemini");
        }

        if (response.statusCode() != 200) {
            String body = String.join("\n", response.body().toList());
            throw new IllegalStateException(errorMessage(response.statusCode(), body));
        }

        StringBuilder fullText = new StringBuilder();
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
        }
        // without SSE framing the endpoint returns one JSON array of chunks
        if (state.chunks == 0 && !nonSse.isEmpty()) {
            JsonNode array = jsonMapper.readTree(nonSse.toString());
            for (JsonNode chunk : array.isArray() ? array : List.of(array)) {
                handleChunk(chunk, fullText, onDelta, state);
            }
        }

        String note = closingNote(state.finishReason);
        if (note != null) {
            if (state.finishReason == null) {
                log.warn("Gemini stream for project {} ended without a finish reason after {} chunks ({} characters)",
                        request.projectId(), state.chunks, fullText.length());
            }
            emit(fullText, onDelta, "\n<message phase=\"completed\">" + note + "</message>");
        }
        long output = Math.max(0, state.totalTokens - state.promptTokens);
        return new GenerationResult(fullText.toString(), state.promptTokens, output, gemini.model());
    }

    /**
     * The note shown after a reply that did not end normally, or null when Gemini finished on its own.
     * A stream that closes without any finish reason was cut off, and its unfinished file is not saved.
     */
    static String closingNote(String finishReason) {
        if (finishReason == null) {
            return "The connection to Gemini closed before the answer was complete, so unfinished files were not saved. "
                    + "Send the request again.";
        }
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

    /** Reads one streamed chunk: text parts, finish reason and token usage. */
    static void handleChunk(JsonNode chunk, StringBuilder fullText, Consumer<String> onDelta, StreamState state) {
        state.chunks++;
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

    private String errorMessage(int status, String body) {
        String detail = body;
        try {
            detail = jsonMapper.readTree(body).path("error").path("message").asString(body);
        } catch (RuntimeException ignored) {
            // not JSON: keep the raw body
        }
        detail = detail.strip().replaceAll("\\.+$", "");
        return switch (status) {
            case 400, 403 -> "Gemini rejected the request (" + status + "): " + detail
                    + ". Check GEMINI_API_KEY and GEMINI_MODEL.";
            case 404 -> "Gemini model '" + properties.gemini().model() + "' was not found. Set GEMINI_MODEL to a model listed in Google AI Studio.";
            case 429 -> "Gemini rate limit reached (free tier). Wait a minute and try again.";
            default -> "Gemini API error " + status + ": " + detail;
        };
    }

    private static void emit(StringBuilder fullText, Consumer<String> onDelta, String text) {
        fullText.append(text);
        onDelta.accept(text);
    }

    static final class StreamState {
        int chunks;
        String finishReason;
        long promptTokens;
        long totalTokens;
    }
}
