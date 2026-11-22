package com.springproject.lovable_clone.intelligence.llm;

import com.springproject.lovable_clone.common.enums.ChatEventType;
import com.springproject.lovable_clone.intelligence.config.AiProperties;
import com.springproject.lovable_clone.intelligence.llm.CodeGenerator.GenerationRequest;
import com.springproject.lovable_clone.intelligence.llm.CodeGenerator.HistoryTurn;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiCodeGeneratorTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final AiProperties properties = new AiProperties("gemini", 12,
            new AiProperties.Gemini("test-key", "gemini-3.5-flash-lite", 32768));
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

        GeminiCodeGenerator.handleChunk(json.readTree("""
                {"candidates":[{"content":{"role":"model","parts":[{"text":"<message>Hi"}]}}]}"""), full, deltas::add, state);
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
    void unfinishedRepliesGetANote() {
        assertThat(GeminiCodeGenerator.closingNote("STOP")).isNull();
        assertThat(GeminiCodeGenerator.closingNote(null)).contains("closed before the answer was complete");
        assertThat(GeminiCodeGenerator.closingNote("MAX_TOKENS")).contains("output limit");
        assertThat(GeminiCodeGenerator.closingNote("SAFETY")).contains("stopped early (SAFETY)");
    }

    @Test
    void cutOffFileIsDroppedButTheNoteIsShown() {
        String cutOff = "<message phase=\"planning\">I will build it.</message>\n<file path=\"src/App.tsx\">import React"
                + "\n<message phase=\"completed\">" + GeminiCodeGenerator.closingNote(null) + "</message>";

        var events = new LlmResponseParser().parse(cutOff);

        assertThat(events).extracting(LlmResponseParser.ParsedEvent::type)
                .containsExactly(ChatEventType.MESSAGE, ChatEventType.MESSAGE);
        assertThat(events.get(1).content()).contains("Send the request again");
    }

    @Test
    void providerIsChosenFromConfiguration() {
        assertThat(properties.resolveProvider()).isEqualTo(AiProperties.Provider.GEMINI);
        var auto = new AiProperties("auto", 12, new AiProperties.Gemini("key", "m", 1));
        assertThat(auto.resolveProvider()).isEqualTo(AiProperties.Provider.GEMINI);
        var noKey = new AiProperties("auto", 12, new AiProperties.Gemini(null, "m", 1));
        assertThat(noKey.resolveProvider()).isEqualTo(AiProperties.Provider.DEMO);
        var forcedDemo = new AiProperties("demo", 12, new AiProperties.Gemini("key", "m", 1));
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
}
