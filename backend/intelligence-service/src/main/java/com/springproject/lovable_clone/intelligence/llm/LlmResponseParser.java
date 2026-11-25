package com.springproject.lovable_clone.intelligence.llm;

import com.springproject.lovable_clone.common.enums.ChatEventType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a tagged assistant reply into ordered events:
 * {@code <message>} -> MESSAGE, {@code <tool args="...">} -> TOOL_LOG, {@code <file path="...">} -> FILE_EDIT.
 */
@Component
public class LlmResponseParser {

    private static final Pattern TAG = Pattern.compile(
            "<(message|file|tool)(\\s[^>]*)?>([\\s\\S]*?)</\\1>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE = Pattern.compile("(path|args|phase)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern CODE_FENCE = Pattern.compile("^```[a-zA-Z0-9]*\\s*\\n([\\s\\S]*?)\\n?```\\s*$");
    // files in which a bare HTML closing tag can never be valid code
    private static final Pattern CODE_FILE = Pattern.compile("(?i).+\\.(tsx?|jsx?|mjs|cjs|css|json)$");
    // models sometimes end such a file with "</script>" or "</html>", which breaks the build
    private static final Pattern TRAILING_CLOSING_TAGS = Pattern.compile("(\\s*\\n\\s*</[a-zA-Z][a-zA-Z0-9-]*>)+\\s*$");

    public record ParsedEvent(ChatEventType type, String content, String filePath, String metadata) {}

    public List<ParsedEvent> parse(String response) {
        List<ParsedEvent> events = new ArrayList<>();
        if (response == null || response.isBlank()) {
            return events;
        }
        Matcher matcher = TAG.matcher(response);
        Map<String, Integer> fileIndex = new HashMap<>();
        while (matcher.find()) {
            String tag = matcher.group(1).toLowerCase();
            Map<String, String> attributes = attributes(matcher.group(2));
            String body = matcher.group(3);
            switch (tag) {
                case "message" -> events.add(new ParsedEvent(ChatEventType.MESSAGE, body.trim(), null, attributes.get("phase")));
                case "tool" -> events.add(new ParsedEvent(ChatEventType.TOOL_LOG, body.trim(), null, attributes.get("args")));
                case "file" -> {
                    String path = attributes.get("path");
                    if (path == null || path.isBlank()) {
                        continue;
                    }
                    path = path.trim().replaceFirst("^/+", "");
                    ParsedEvent event = new ParsedEvent(ChatEventType.FILE_EDIT, cleanFileBody(path, body), path, null);
                    // if a file is emitted twice, the last version wins
                    Integer existing = fileIndex.get(path);
                    if (existing != null) {
                        events.set(existing, event);
                    } else {
                        fileIndex.put(path, events.size());
                        events.add(event);
                    }
                }
                default -> { }
            }
        }
        if (events.isEmpty()) {
            // the model ignored the protocol: keep the text as a plain message
            events.add(new ParsedEvent(ChatEventType.MESSAGE, response.trim(), null, null));
        }
        return events;
    }

    /**
     * Strips a leading newline, an accidental markdown fence around the file body, and stray HTML closing tags at
     * the end of a code file.
     */
    static String cleanFileBody(String path, String body) {
        String content = body.startsWith("\r\n") ? body.substring(2) : body.startsWith("\n") ? body.substring(1) : body;
        Matcher fence = CODE_FENCE.matcher(content.trim());
        if (fence.matches()) {
            content = fence.group(1);
        }
        if (CODE_FILE.matcher(path).matches()) {
            content = TRAILING_CLOSING_TAGS.matcher(content).replaceFirst("");
        }
        return content.endsWith("\n") ? content : content + "\n";
    }

    private static Map<String, String> attributes(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw != null) {
            Matcher matcher = ATTRIBUTE.matcher(raw);
            while (matcher.find()) {
                map.put(matcher.group(1), matcher.group(2));
            }
        }
        return map;
    }
}
