package com.springproject.lovable_clone.intelligence.llm;

import com.springproject.lovable_clone.common.enums.ChatEventType;
import com.springproject.lovable_clone.intelligence.llm.LlmResponseParser.ParsedEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmResponseParserTest {

    private final LlmResponseParser parser = new LlmResponseParser();

    @Test
    void parsesMessagesToolLogsAndFilesInOrder() {
        String reply = """
                <message phase="planning">I will update **App.tsx**.</message>
                <tool args="src/App.tsx">Reading App.tsx...</tool>
                <file path="src/App.tsx">
                export default function App() {
                  return <h1>Hi</h1>
                }
                </file>
                <message phase="completed">Done.</message>
                """;

        List<ParsedEvent> events = parser.parse(reply);

        assertThat(events).extracting(ParsedEvent::type).containsExactly(
                ChatEventType.MESSAGE, ChatEventType.TOOL_LOG, ChatEventType.FILE_EDIT, ChatEventType.MESSAGE);
        assertThat(events.get(0).metadata()).isEqualTo("planning");
        assertThat(events.get(1).metadata()).isEqualTo("src/App.tsx");
        assertThat(events.get(2).filePath()).isEqualTo("src/App.tsx");
        assertThat(events.get(2).content()).startsWith("export default function App()").endsWith("}\n");
    }

    @Test
    void stripsMarkdownFencesAndLeadingSlash() {
        String reply = "<file path=\"/src/a.ts\">\n```ts\nexport const a = 1\n```\n</file>";

        ParsedEvent event = parser.parse(reply).getFirst();

        assertThat(event.filePath()).isEqualTo("src/a.ts");
        assertThat(event.content()).isEqualTo("export const a = 1\n");
    }

    @Test
    void lastVersionOfARepeatedFileWins() {
        String reply = "<file path=\"x.ts\">one</file><file path=\"x.ts\">two</file>";

        List<ParsedEvent> events = parser.parse(reply);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().content()).isEqualTo("two\n");
    }

    @Test
    void unfinishedFileTagIsIgnored() {
        String reply = "<message>Working</message><file path=\"a.ts\">partial cont";

        assertThat(parser.parse(reply)).extracting(ParsedEvent::type).containsExactly(ChatEventType.MESSAGE);
    }

    @Test
    void untaggedReplyBecomesAPlainMessage() {
        List<ParsedEvent> events = parser.parse("Sure, here is an idea.");

        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(ChatEventType.MESSAGE);
            assertThat(e.content()).isEqualTo("Sure, here is an idea.");
        });
    }
}
