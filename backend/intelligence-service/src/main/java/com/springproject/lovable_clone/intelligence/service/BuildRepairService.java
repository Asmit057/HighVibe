package com.springproject.lovable_clone.intelligence.service;

import com.springproject.lovable_clone.common.dto.BuildCheckRequest;
import com.springproject.lovable_clone.common.dto.BuildCheckResult;
import com.springproject.lovable_clone.common.dto.FileNode;
import com.springproject.lovable_clone.common.enums.ChatEventType;
import com.springproject.lovable_clone.intelligence.client.WorkspaceClient;
import com.springproject.lovable_clone.intelligence.llm.CodeGenerator;
import com.springproject.lovable_clone.intelligence.llm.CodeGenerator.GenerationRequest;
import com.springproject.lovable_clone.intelligence.llm.CodeGenerator.GenerationResult;
import com.springproject.lovable_clone.intelligence.llm.CodeGenerator.HistoryTurn;
import com.springproject.lovable_clone.intelligence.llm.CodeGenerator.ReplyListener;
import com.springproject.lovable_clone.intelligence.llm.LlmResponseParser;
import com.springproject.lovable_clone.intelligence.llm.PromptUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Test-builds the files of an AI answer before they are saved. When the build fails, the model is asked to fix exactly
 * the reported error, and its fix is appended to the same answer (a later version of a file wins), up to
 * {@code app.ai.repair-rounds} times. A check that cannot run never blocks the answer.
 */
@Slf4j
@Service
public class BuildRepairService {

    private static final Pattern FILE_POSITION = Pattern.compile("(?i)\\.(tsx?|jsx?|css|html|json):\\d+");

    private final WorkspaceClient workspaceClient;
    private final LlmResponseParser parser;
    private final int maxRounds;

    public BuildRepairService(WorkspaceClient workspaceClient, LlmResponseParser parser,
                              @Value("${app.ai.repair-rounds:2}") int maxRounds) {
        this.workspaceClient = workspaceClient;
        this.parser = parser;
        this.maxRounds = maxRounds;
    }

    public GenerationResult repair(CodeGenerator generator, GenerationRequest request, GenerationResult first,
                                   ReplyListener listener) {
        if (maxRounds <= 0) {
            return first;
        }
        Long projectId = request.projectId();
        StringBuilder reply = new StringBuilder(first.fullText());
        long inputTokens = first.inputTokens();
        long outputTokens = first.outputTokens();
        String model = first.model();

        for (int round = 0; ; round++) {
            Map<String, String> files = filesOf(reply.toString());
            if (files.isEmpty()) {
                break;
            }
            BuildCheckResult check = check(projectId, files);
            if (check.ok()) {
                if (round > 0) {
                    log.info("Project {} builds after {} repair round(s)", projectId, round);
                }
                break;
            }
            String error = check.output();
            String summary = summary(error);
            if (round >= maxRounds) {
                log.warn("Project {} still does not build after {} repair round(s): {}", projectId, round, summary);
                append(reply, listener, "\n<message phase=\"completed\">The app still has a build error, so the preview may"
                        + " show it: `" + summary + "`. Ask me to fix it.</message>");
                break;
            }
            log.warn("Test build of project {} failed, repair round {}: {}", projectId, round + 1, summary);
            append(reply, listener, "\n<message phase=\"planning\">The code did not build (`" + summary
                    + "`), so I am fixing it.</message>\n");
            String before = reply.toString();
            GenerationResult fix;
            try {
                fix = generator.generate(fixRequest(request, before, files, error), keepingPrefix(listener, before));
            } catch (RuntimeException e) {
                log.warn("Repair of project {} failed: {}", projectId, e.getMessage());
                append(reply, listener, "\n<message phase=\"completed\">I could not fix the build error automatically: `"
                        + summary + "`. Ask me to fix it.</message>");
                break;
            }
            reply.append(fix.fullText());
            inputTokens += fix.inputTokens();
            outputTokens += fix.outputTokens();
            model = fix.model();
        }
        return new GenerationResult(reply.toString(), inputTokens, outputTokens, model);
    }

    /** The files of the answer so far: path to content, the last version of each file. */
    private Map<String, String> filesOf(String reply) {
        Map<String, String> files = new LinkedHashMap<>();
        for (LlmResponseParser.ParsedEvent event : parser.parse(reply)) {
            if (event.type() == ChatEventType.FILE_EDIT) {
                files.put(event.filePath(), event.content());
            }
        }
        return files;
    }

    private BuildCheckResult check(Long projectId, Map<String, String> files) {
        try {
            BuildCheckResult result = workspaceClient.checkBuild(projectId, new BuildCheckRequest(files));
            if (result.skipped()) {
                log.info("Test build of project {} skipped: {}", projectId, result.output());
            }
            return result;
        } catch (RuntimeException e) {
            log.warn("Test build of project {} could not run: {}", projectId, e.getMessage());
            return BuildCheckResult.skipped(e.getMessage());
        }
    }

    /** The same request, now asking to fix the build error, with the new files as the current project. */
    static GenerationRequest fixRequest(GenerationRequest request, String replySoFar, Map<String, String> files, String error) {
        List<HistoryTurn> history = new ArrayList<>(request.history());
        history.add(new HistoryTurn(true, request.userMessage()));
        history.add(new HistoryTurn(false, PromptUtils.withoutFileBodies(replySoFar)));

        List<FileNode> nodes = new ArrayList<>(request.files());
        files.forEach((path, content) -> {
            if (nodes.stream().noneMatch(node -> node.path().equals(path))) {
                nodes.add(new FileNode(path, (long) content.length(), null));
            }
        });

        String message = "The app does not build. `vite build` reported:\n\n" + error + "\n\n"
                + "Fix the cause of this error. Change as little as possible and output only the files you change, "
                + "each one complete.";
        return new GenerationRequest(request.projectId(), message, history, nodes,
                path -> files.containsKey(path) ? files.get(path) : request.readFile().apply(path));
    }

    /**
     * The line of the build output that names the problem, for the chat and the log: preferably "file:line: ...",
     * then a plugin message such as "[@tailwindcss/vite] ...", never a stack frame of the build tool itself.
     */
    static String summary(String error) {
        List<String> lines = error == null ? List.of() : error.lines().map(String::strip)
                .filter(line -> !line.isEmpty() && !line.equalsIgnoreCase("error during build:")
                        && !line.startsWith("at ") && !line.contains("node_modules") && !line.startsWith("✗"))
                .toList();
        String line = lines.stream().filter(l -> FILE_POSITION.matcher(l).find() && !l.startsWith("file:")).findFirst()
                .or(() -> lines.stream().filter(l -> l.startsWith("[")).findFirst())
                .or(() -> lines.stream().filter(l -> l.toLowerCase().contains("error")).findFirst())
                .orElse(lines.isEmpty() ? "unknown error" : lines.getFirst());
        // the summary is shown inside a chat message: no backticks, and no "<" that could look like a tag
        line = line.replace('`', '\'').replace('<', '‹');
        return line.length() > 200 ? line.substring(0, 200) + "..." : line;
    }

    /** When the model is asked again mid-fix, the browser is reset to the answer before the fix, not to nothing. */
    private static ReplyListener keepingPrefix(ReplyListener listener, String prefix) {
        return new ReplyListener() {
            @Override
            public void delta(String text) {
                listener.delta(text);
            }

            @Override
            public void restart() {
                listener.restart();
                listener.delta(prefix);
            }
        };
    }

    private static void append(StringBuilder reply, ReplyListener listener, String text) {
        reply.append(text);
        listener.delta(text);
    }
}
