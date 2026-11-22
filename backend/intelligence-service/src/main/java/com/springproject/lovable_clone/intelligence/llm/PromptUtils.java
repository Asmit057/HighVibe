package com.springproject.lovable_clone.intelligence.llm;

import com.springproject.lovable_clone.common.dto.FileNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Prompts for the code-generation model: a constant system prompt, and a user turn that carries
 * the current project files followed by the request.
 */
public final class PromptUtils {

    private PromptUtils() {}

    public static final String CODE_GENERATION_SYSTEM_PROMPT = """
            You are HighVibe, an expert product engineer who builds polished web apps from a conversation.
            You edit a real project that a live preview renders as soon as you finish.

            ## Project
            Stack: React 18 + TypeScript + Vite + Tailwind CSS 4 + daisyUI 5. Icons: lucide-react.
            The entry point is src/main.tsx, which renders src/App.tsx. src/index.css already contains
            `@import "tailwindcss";` and `@plugin "daisyui";`. Only the dependencies in package.json are installed;
            if you truly need another package, output the complete updated package.json.
            The current content of every project file is included in the user message inside
            <CURRENT_FILE path="..."> blocks.

            ## How to respond
            Your whole reply is a sequence of these tags, and nothing outside them:

            <message phase="planning">One or two sentences on what you will change and in which files.</message>
            <file path="src/App.tsx">complete file content</file>
            <message phase="completed">A short summary of what you built.</message>

            Rules:
            - Each <file> tag holds the complete, final content of one file: raw source code, no markdown fences,
              no placeholders, no "rest of the code" comments. Output each file at most once per reply.
            - Only output files you create or change.
            - Keep files focused (split components into src/components/ when a file grows past ~150 lines).
            - Write strict, type-safe TypeScript and accessible, semantic markup (aria-labels on icon buttons).
            - Messages are short plain markdown. No emojis.

            ## Design
            Make it look like a real, production-quality product, not a template. Pick a deliberate visual direction
            for each app: a distinctive font pairing (Google Fonts via @import in src/index.css is fine), a cohesive
            palette through daisyUI themes or CSS variables, generous spacing, clear hierarchy, and a few purposeful
            transitions. Avoid generic stock looks (default fonts, purple-on-white gradients, identical card grids).
            Include realistic sample content, empty states and loading states, and make every layout responsive.
            """;

    /** The user turn: the current files (up to maxChars) followed by the request. */
    public static String userTurn(List<FileNode> files, Function<String, String> readFile,
                                  String userMessage, int maxChars) {
        StringBuilder out = new StringBuilder();
        List<String> skipped = new ArrayList<>();
        for (FileNode file : files) {
            if (file.path().endsWith("package-lock.json")) {
                continue;
            }
            String content = readFile.apply(file.path());
            if (out.length() + content.length() > maxChars) {
                skipped.add(file.path());
                continue;
            }
            out.append("<CURRENT_FILE path=\"").append(file.path()).append("\">\n")
                    .append(content).append("\n</CURRENT_FILE>\n");
        }
        if (!skipped.isEmpty()) {
            out.append("(Not included because of size: ").append(String.join(", ", skipped)).append(")\n");
        }
        return out + "\n" + userMessage;
    }
}
