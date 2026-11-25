package com.springproject.lovable_clone.intelligence.llm;

import com.springproject.lovable_clone.common.enums.ChatEventType;
import com.springproject.lovable_clone.intelligence.config.AiProperties;
import com.springproject.lovable_clone.intelligence.llm.CodeGenerator.GenerationRequest;
import com.springproject.lovable_clone.intelligence.llm.CodeGenerator.HistoryTurn;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiCodeGeneratorTest {

    private static final String HELLO = """
            {"candidates":[{"content":{"parts":[{"text":"<message>Hi"}]}}]}""";
    private static final String HELLO_END = """
            {"candidates":[{"content":{"parts":[{"text":"</message>"}]},"finishReason":"STOP"}],
             "usageMetadata":{"promptTokenCount":10,"totalTokenCount":15}}""".replace("\n", "");
    // the exact shapes Google returns
    private static final String OVERLOADED = """
            {"error":{"code":503,"message":"This model is currently experiencing high demand. Please try again later.","status":"UNAVAILABLE"}}""";
    private static final String RATE_LIMITED = """
            {"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED",
             "details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"37s"}]}}""";
    private static final String BAD_KEY = """
            {"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT",
             "details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"API_KEY_INVALID"}]}}""";

    private final JsonMapper json = JsonMapper.builder().build();
    private final AiProperties properties = new AiProperties("gemini", 12,
            new AiProperties.Gemini("test-key", null, "gemini-3.5-flash-lite", null, 32768, 4));
    private final GeminiCodeGenerator generator = new GeminiCodeGenerator(properties, json);

    @Test
    void requestHasSystemInstructionAlternatingTurnsAndOutputLimit() {
        var request = new GenerationRequest(1L, "make it blue",
                List.of(new HistoryTurn(true, "build a calculator"), new HistoryTurn(false, "<message>done</message>")),
                List.of(), path -> "");

        JsonNode body = json.readTree(generator.requestBody(request, "make it blue", 32768));

        assertThat(body.at("/systemInstruction/parts/0/text").asString()).contains("HighVibe").contains("CURRENT_FILE");
        assertThat(body.at("/contents/0/role").asString()).isEqualTo("user");
        assertThat(body.at("/contents/1/role").asString()).isEqualTo("model");
        assertThat(body.at("/contents/2/role").asString()).isEqualTo("user");
        assertThat(body.at("/contents/2/parts/0/text").asString()).isEqualTo("make it blue");
        assertThat(body.at("/generationConfig/maxOutputTokens").asInt()).isEqualTo(32768);
    }

    @Test
    void streamedChunksAreForwardedAndUsageIsRead() {
        StringBuilder full = new StringBuilder();
        List<String> deltas = new ArrayList<>();
        var state = new GeminiCodeGenerator.StreamState();

        GeminiCodeGenerator.handleChunk(json.readTree(HELLO), full, deltas::add, state);
        GeminiCodeGenerator.handleChunk(json.readTree("""
                {"candidates":[{"content":{"parts":[{"text":"thinking...","thought":true},{"text":"</message>"}]},
                  "finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":1200,"candidatesTokenCount":40,"totalTokenCount":1300}}"""),
                full, deltas::add, state);

        assertThat(full.toString()).isEqualTo("<message>Hi</message>");
        assertThat(deltas).containsExactly("<message>Hi", "</message>");
        assertThat(state.finishReason).isEqualTo("STOP");
        assertThat(state.promptTokens).isEqualTo(1200);
        assertThat(state.totalTokens).isEqualTo(1300);
    }

    @Test
    void anErrorSentInsideTheStreamIsRecorded() {
        var state = new GeminiCodeGenerator.StreamState();

        GeminiCodeGenerator.handleChunk(json.readTree(OVERLOADED), new StringBuilder(), text -> { }, state);

        assertThat(state.error.path("status").asString()).isEqualTo("UNAVAILABLE");
        assertThat(state.finishReason).isNull();
    }

    @Test
    void repliesThatEndEarlyGetANote() {
        assertThat(GeminiCodeGenerator.closingNote("STOP")).isNull();
        assertThat(GeminiCodeGenerator.closingNote("MAX_TOKENS")).contains("output limit");
        assertThat(GeminiCodeGenerator.closingNote("SAFETY")).contains("stopped early (SAFETY)");
    }

    @Test
    void fileCutOffByTheOutputLimitIsDroppedButTheNoteIsShown() {
        String cutOff = "<message phase=\"planning\">I will build it.</message>\n<file path=\"src/App.tsx\">import React"
                + "\n<message phase=\"completed\">" + GeminiCodeGenerator.closingNote("MAX_TOKENS") + "</message>";

        var events = new LlmResponseParser().parse(cutOff);

        assertThat(events).extracting(LlmResponseParser.ParsedEvent::type)
                .containsExactly(ChatEventType.MESSAGE, ChatEventType.MESSAGE);
        assertThat(events.get(1).content()).contains("Ask me to continue");
    }

    @Test
    void googleErrorsAreSortedByWhatHelps() {
        GeminiFailure rateLimited = GeminiFailure.fromError(429, json.readTree(RATE_LIMITED).path("error"), "m");
        assertThat(rateLimited.kind()).isEqualTo(GeminiFailure.Kind.KEY);
        assertThat(rateLimited.waitHint()).isEqualTo(Duration.ofSeconds(37));

        GeminiFailure badKey = GeminiFailure.fromError(400, json.readTree(BAD_KEY).path("error"), "m");
        assertThat(badKey.kind()).isEqualTo(GeminiFailure.Kind.KEY);
        assertThat(badKey.getMessage()).contains("the API key was rejected (400 INVALID_ARGUMENT: API key not valid");

        GeminiFailure overloaded = GeminiFailure.fromError(200, json.readTree(OVERLOADED).path("error"), "m");
        assertThat(overloaded.kind()).isEqualTo(GeminiFailure.Kind.BUSY);
        assertThat(overloaded.getMessage()).contains("503 UNAVAILABLE: This model is currently experiencing high demand");

        GeminiFailure unknownModel = GeminiFailure.fromError(404,
                json.readTree("{\"error\":{\"code\":404,\"status\":\"NOT_FOUND\"}}").path("error"), "old-model");
        assertThat(unknownModel.kind()).isEqualTo(GeminiFailure.Kind.MODEL);
        assertThat(unknownModel.getMessage()).contains("model 'old-model' was not found");
    }

    @Test
    void aRateLimitedKeyRestsAndTheNextKeyAnswers() throws IOException {
        try (var gemini = new FakeGemini()) {
            gemini.error(429, RATE_LIMITED);
            gemini.stream(HELLO, HELLO_END);
            var listener = new RecordingListener();

            var result = generatorFor(gemini, 4, "key-a", "key-b").generate(request(), listener);

            assertThat(result.fullText()).isEqualTo("<message>Hi</message>");
            assertThat(result.inputTokens()).isEqualTo(10);
            assertThat(gemini.keysUsed).containsExactly("key-a", "key-b");
            assertThat(listener.restarts).isZero();
        }
    }

    @Test
    void aRejectedKeyIsSkippedByLaterGenerations() throws IOException {
        try (var gemini = new FakeGemini()) {
            gemini.error(400, BAD_KEY);
            gemini.stream(HELLO, HELLO_END);
            gemini.stream(HELLO, HELLO_END);
            GeminiCodeGenerator twoKeys = generatorFor(gemini, 4, "key-a", "key-b");

            twoKeys.generate(request(), new RecordingListener());
            twoKeys.generate(request(), new RecordingListener());

            // key-a rests for ten minutes, so the second generation goes straight to key-b
            assertThat(gemini.keysUsed).containsExactly("key-a", "key-b", "key-b");
        }
    }

    @Test
    void anOverloadInsideTheStreamIsRetriedAndTheStreamedTextIsReset() throws IOException {
        try (var gemini = new FakeGemini()) {
            gemini.stream(HELLO, OVERLOADED);
            gemini.stream(HELLO, HELLO_END);
            var listener = new RecordingListener();

            var result = generatorFor(gemini, 4, "key-a").generate(request(), listener);

            assertThat(result.fullText()).isEqualTo("<message>Hi</message>");
            assertThat(listener.restarts).isEqualTo(1);
            assertThat(listener.text).hasToString("<message>Hi</message>");
            assertThat(gemini.keysUsed).containsExactly("key-a", "key-a");
        }
    }

    @Test
    void aStreamThatStopsWithoutFinishingIsRetried() throws IOException {
        try (var gemini = new FakeGemini()) {
            gemini.stream(HELLO);
            gemini.stream(HELLO, HELLO_END);
            var listener = new RecordingListener();

            var result = generatorFor(gemini, 4, "key-a").generate(request(), listener);

            assertThat(result.fullText()).isEqualTo("<message>Hi</message>");
            assertThat(listener.restarts).isEqualTo(1);
        }
    }

    @Test
    void givesUpWithGooglesOwnMessageAndNeverTheKey() throws IOException {
        try (var gemini = new FakeGemini()) {
            for (int i = 0; i < 3; i++) {
                gemini.error(503, OVERLOADED);
            }

            assertThatThrownBy(() -> generatorFor(gemini, 3, "secret-key-a").generate(request(), new RecordingListener()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("503 UNAVAILABLE: This model is currently experiencing high demand")
                    .hasMessageContaining("Tried 3 times with test-model")
                    .hasMessageNotContaining("secret-key-a");
            assertThat(gemini.keysUsed).hasSize(3);
        }
    }

    @Test
    void anUnknownModelWithoutFallbackIsNotRetried() throws IOException {
        try (var gemini = new FakeGemini()) {
            gemini.error(404, "{\"error\":{\"code\":404,\"message\":\"models/test-model is not found\",\"status\":\"NOT_FOUND\"}}");

            assertThatThrownBy(() -> generatorFor(gemini, 4, "key-a", "key-b").generate(request(), new RecordingListener()))
                    .hasMessageContaining("model 'test-model' was not found");
            assertThat(gemini.keysUsed).hasSize(1);
        }
    }

    @Test
    void anOverloadedModelHandsOverToTheFallbackWhichLaterGenerationsUseToo() throws IOException {
        try (var gemini = new FakeGemini()) {
            gemini.stream(HELLO, OVERLOADED);
            gemini.stream(HELLO, HELLO_END);
            gemini.stream(HELLO, HELLO_END);
            var clock = new FallbackPoolTest.MovableClock();
            GeminiCodeGenerator withBackup = generatorFor(gemini, List.of("main", "backup"), clock, 4, "key-a");
            var listener = new RecordingListener();

            var first = withBackup.generate(request(), listener);
            var second = withBackup.generate(request(), new RecordingListener());

            assertThat(first.model()).isEqualTo("backup");
            assertThat(listener.restarts).isEqualTo(1);
            // the busy main model rests for five minutes, so the next generation goes straight to the backup
            assertThat(second.model()).isEqualTo("backup");
            assertThat(gemini.modelsUsed).containsExactly("main", "backup", "backup");
        }
    }

    @Test
    void theMainModelIsTriedAgainWhenItsRestIsOver() throws IOException {
        try (var gemini = new FakeGemini()) {
            gemini.error(503, OVERLOADED);
            gemini.stream(HELLO, HELLO_END);
            gemini.stream(HELLO, HELLO_END);
            var clock = new FallbackPoolTest.MovableClock();
            GeminiCodeGenerator withBackup = generatorFor(gemini, List.of("main", "backup"), clock, 4, "key-a");

            withBackup.generate(request(), new RecordingListener());
            clock.advance(Duration.ofMinutes(5));
            var later = withBackup.generate(request(), new RecordingListener());

            assertThat(later.model()).isEqualTo("main");
            assertThat(gemini.modelsUsed).containsExactly("main", "backup", "main");
        }
    }

    @Test
    void aMissingModelIsSkippedForTheFallback() throws IOException {
        try (var gemini = new FakeGemini()) {
            gemini.error(404, "{\"error\":{\"code\":404,\"message\":\"models/old is not found\",\"status\":\"NOT_FOUND\"}}");
            gemini.stream(HELLO, HELLO_END);

            var result = generatorFor(gemini, List.of("old", "backup"), Clock.systemUTC(), 4, "key-a")
                    .generate(request(), new RecordingListener());

            assertThat(result.model()).isEqualTo("backup");
            assertThat(gemini.modelsUsed).containsExactly("old", "backup");
        }
    }

    @Test
    void keysAndModelsAreMergedWithoutBlanksOrRepeats() {
        var gemini = new AiProperties.Gemini("key-a", List.of(" key-b ", "", "key-a", "key-c"), "m", List.of("m", " backup "), 1, 4);

        assertThat(gemini.keys()).containsExactly("key-a", "key-b", "key-c");
        assertThat(gemini.models()).containsExactly("m", "backup");
        assertThat(new AiProperties.Gemini(null, null, "m", null, 1, 4).keys()).isEmpty();
    }

    @Test
    void providerIsChosenFromConfiguration() {
        assertThat(properties.resolveProvider()).isEqualTo(AiProperties.Provider.GEMINI);
        var auto = new AiProperties("auto", 12, new AiProperties.Gemini("key", null, "m", null, 1, 4));
        assertThat(auto.resolveProvider()).isEqualTo(AiProperties.Provider.GEMINI);
        var onlyExtraKeys = new AiProperties("auto", 12, new AiProperties.Gemini(null, List.of("key"), "m", null, 1, 4));
        assertThat(onlyExtraKeys.resolveProvider()).isEqualTo(AiProperties.Provider.GEMINI);
        var noKey = new AiProperties("auto", 12, new AiProperties.Gemini(null, null, "m", null, 1, 4));
        assertThat(noKey.resolveProvider()).isEqualTo(AiProperties.Provider.DEMO);
        var forcedDemo = new AiProperties("demo", 12, new AiProperties.Gemini("key", null, "m", null, 1, 4));
        assertThat(forcedDemo.resolveProvider()).isEqualTo(AiProperties.Provider.DEMO);
    }

    @Test
    void conversationStartsWithUserAndMergesSameSideTurns() {
        List<HistoryTurn> turns = Conversation.alternating(List.of(
                new HistoryTurn(false, "orphan reply"),
                new HistoryTurn(true, "first"),
                new HistoryTurn(true, "unanswered")), "now");

        assertThat(turns).singleElement().satisfies(t -> {
            assertThat(t.fromUser()).isTrue();
            assertThat(t.text()).isEqualTo("first\n\nunanswered\n\nnow");
        });
    }

    private GeminiCodeGenerator generatorFor(FakeGemini gemini, int maxAttempts, String... keys) {
        return generatorFor(gemini, List.of("test-model"), Clock.systemUTC(), maxAttempts, keys);
    }

    private GeminiCodeGenerator generatorFor(FakeGemini gemini, List<String> models, Clock clock, int maxAttempts,
                                             String... keys) {
        var settings = new AiProperties("gemini", 12, new AiProperties.Gemini(keys[0],
                Arrays.asList(keys).subList(1, keys.length), models.getFirst(), models.subList(1, models.size()),
                1000, maxAttempts));
        return new GeminiCodeGenerator(settings, json, gemini.baseUrl(), Duration.ofMillis(1), clock);
    }

    private static GenerationRequest request() {
        return new GenerationRequest(1L, "build it", List.of(), List.of(), path -> "");
    }

    private static final class RecordingListener implements CodeGenerator.ReplyListener {
        final StringBuilder text = new StringBuilder();
        int restarts;

        @Override
        public void delta(String delta) {
            text.append(delta);
        }

        @Override
        public void restart() {
            restarts++;
            text.setLength(0);
        }
    }

    /** A local stand-in for the Gemini API that answers each call with the next scripted reply. */
    private static final class FakeGemini implements AutoCloseable {

        private record Reply(int status, String contentType, String body) {}

        private final HttpServer server;
        private final Queue<Reply> replies = new ConcurrentLinkedQueue<>();
        final List<String> keysUsed = new CopyOnWriteArrayList<>();
        final List<String> modelsUsed = new CopyOnWriteArrayList<>();

        FakeGemini() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                keysUsed.add(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
                String path = exchange.getRequestURI().getPath();
                modelsUsed.add(path.substring(path.lastIndexOf('/') + 1, path.indexOf(':')));
                exchange.getRequestBody().readAllBytes();
                Reply reply = replies.remove();
                byte[] bytes = reply.body().getBytes(UTF_8);
                exchange.getResponseHeaders().add("Content-Type", reply.contentType());
                exchange.sendResponseHeaders(reply.status(), bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta/models/";
        }

        void stream(String... chunks) {
            replies.add(new Reply(200, "text/event-stream",
                    Arrays.stream(chunks).map(chunk -> "data: " + chunk.replace("\n", "") + "\r\n\r\n").collect(joining())));
        }

        void error(int status, String body) {
            replies.add(new Reply(status, "application/json", body));
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
