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
    void strayHtmlClosingTagsAtTheEndOfACodeFileAreRemoved() {
        // exactly what broke a generated coffee site: a data file that ended with "</script>"
        String reply = "<file path=\"src/data/coffeeData.ts\">\nexport const BEANS = [\n  { id: 'kenya' }\n];\n</script>\n</file>"
                + "<file path=\"index.html\">\n<body>\n  <script type=\"module\" src=\"/src/main.tsx\"></script>\n</body>\n</html>\n</file>";

        List<ParsedEvent> events = parser.parse(reply);

        assertThat(events.get(0).content()).isEqualTo("export const BEANS = [\n  { id: 'kenya' }\n];\n");
        // an HTML file keeps its closing tags
        assertThat(events.get(1).content()).endsWith("</body>\n</html>\n");
    }

    @Test
    void jsxClosingTagsInsideTheCodeAreKept() {
        String reply = "<file path=\"src/App.tsx\">\nexport default function App() {\n  return (\n    <div>\n      <p>Hi</p>\n    </div>\n  )\n}\n</file>";

        assertThat(parser.parse(reply).getFirst().content()).contains("    </div>\n  )\n}\n");
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
