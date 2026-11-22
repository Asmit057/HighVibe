package com.springproject.lovable_clone.intelligence.llm;

import com.springproject.lovable_clone.common.enums.ChatEventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoCodeGeneratorTest {

    @Test
    void titleIsDerivedFromThePrompt() {
        assertThat(DemoCodeGenerator.titleFrom("Build me a habit tracker with streaks and charts"))
                .isEqualTo("Habit Tracker Streaks Charts");
        assertThat(DemoCodeGenerator.titleFrom("A landing page for a specialty coffee roaster"))
                .isEqualTo("Landing Page Specialty Coffee");
        assertThat(DemoCodeGenerator.titleFrom("!!!")).isEqualTo("My HighVibe App");
    }

    @Test
    void streamsAReplyThatFollowsTheTagProtocol() {
        DemoCodeGenerator generator = new DemoCodeGenerator();
        StringBuilder streamed = new StringBuilder();

        CodeGenerator.GenerationResult result = generator.generate(
                new CodeGenerator.GenerationRequest(1L, "A recipe app for <b>busy</b> cooks", List.of(), List.of(), p -> ""),
                streamed::append);

        assertThat(streamed.toString()).isEqualTo(result.fullText());
        assertThat(result.totalTokens()).isPositive();

        var events = new LlmResponseParser().parse(result.fullText());
        assertThat(events).filteredOn(e -> e.type() == ChatEventType.FILE_EDIT)
                .extracting(LlmResponseParser.ParsedEvent::filePath)
                .containsExactly("src/components/FeatureCard.tsx", "src/App.tsx", "index.html");
        // user text is embedded as an escaped string literal, never as raw markup
        String app = events.stream().filter(e -> "src/App.tsx".equals(e.filePath())).findFirst().orElseThrow().content();
        assertThat(app).contains("\\u003cb>busy").doesNotContain("<b>busy");
    }
}
