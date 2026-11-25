package com.springproject.lovable_clone.intelligence.service;

import com.springproject.lovable_clone.common.dto.BuildCheckRequest;
import com.springproject.lovable_clone.common.dto.BuildCheckResult;
import com.springproject.lovable_clone.common.dto.FileNode;
import com.springproject.lovable_clone.common.dto.FileTreeDto;
import com.springproject.lovable_clone.common.enums.ChatEventType;
import com.springproject.lovable_clone.common.enums.ProjectPermission;
import com.springproject.lovable_clone.intelligence.client.WorkspaceClient;
import com.springproject.lovable_clone.intelligence.llm.CodeGenerator;
import com.springproject.lovable_clone.intelligence.llm.CodeGenerator.GenerationRequest;
import com.springproject.lovable_clone.intelligence.llm.CodeGenerator.GenerationResult;
import com.springproject.lovable_clone.intelligence.llm.LlmResponseParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BuildRepairServiceTest {

    private static final String BROKEN = "<message phase=\"planning\">Building it.</message>\n"
            + "<file path=\"src/App.tsx\">export default function App() { return <h1>Hi</h1 }</file>\n"
            + "<file path=\"src/data.ts\">export const A = 1</file>";
    private static final String FIX = "<file path=\"src/App.tsx\">export default function App() { return <h1>Hi</h1> }</file>";
    private static final String VITE_ERROR = """
            error during build:
            [vite:esbuild] Transform failed with 1 error:
            src/App.tsx:1:50: ERROR: Expected ">" but found "}"
            file: src/App.tsx:1:50""";

    private final FakeWorkspace workspace = new FakeWorkspace();
    private final ScriptedGenerator generator = new ScriptedGenerator();
    private final LlmResponseParser parser = new LlmResponseParser();
    private final StringBuilder streamed = new StringBuilder();
    private final CodeGenerator.ReplyListener listener = streamed::append;

    @Test
    void anAnswerThatBuildsIsLeftAlone() {
        workspace.results.add(BuildCheckResult.passed());

        GenerationResult result = service(2).repair(generator, request(), first(BROKEN), listener);

        assertThat(result.fullText()).isEqualTo(BROKEN);
        assertThat(generator.requests).isEmpty();
        assertThat(workspace.checked).singleElement().satisfies(files ->
                assertThat(files).containsOnlyKeys("src/App.tsx", "src/data.ts"));
    }

    @Test
    void aBuildErrorIsSentToTheModelAndItsFixReplacesTheBrokenFile() {
        workspace.results.add(BuildCheckResult.failed(VITE_ERROR));
        workspace.results.add(BuildCheckResult.passed());
        generator.replies.add(FIX);

        GenerationResult result = service(2).repair(generator, request(), first(BROKEN), listener);

        // the model was given the exact error and the broken file as the current project
        GenerationRequest fixRequest = generator.requests.getFirst();
        assertThat(fixRequest.userMessage()).contains("ERROR: Expected \">\" but found \"}\"");
        assertThat(fixRequest.readFile().apply("src/App.tsx")).contains("<h1>Hi</h1 }");
        assertThat(fixRequest.files()).extracting(FileNode::path).contains("src/App.tsx", "src/data.ts");
        // the saved answer has the fixed file (the later version wins) and tells the user what happened
        var files = parser.parse(result.fullText()).stream()
                .filter(e -> e.type() == ChatEventType.FILE_EDIT).toList();
        assertThat(files).extracting(LlmResponseParser.ParsedEvent::filePath).containsExactly("src/App.tsx", "src/data.ts");
        assertThat(files.getFirst().content()).contains("<h1>Hi</h1> }");
        assertThat(result.fullText()).contains("The code did not build (`src/App.tsx:1:50: ERROR: Expected");
        assertThat(result.totalTokens()).isEqualTo(30 + 30);
        assertThat(streamed).hasToString(result.fullText().substring(BROKEN.length()));
    }

    @Test
    void afterTheLastRoundTheUserIsToldWhatStillFails() {
        for (int i = 0; i < 3; i++) {
            workspace.results.add(BuildCheckResult.failed(VITE_ERROR));
        }
        generator.replies.add(BROKEN);
        generator.replies.add(BROKEN);

        GenerationResult result = service(2).repair(generator, request(), first(BROKEN), listener);

        assertThat(generator.requests).hasSize(2);
        assertThat(workspace.checked).hasSize(3);
        assertThat(result.fullText()).endsWith("The app still has a build error, so the preview may show it: "
                + "`src/App.tsx:1:50: ERROR: Expected \">\" but found \"}\"`. Ask me to fix it.</message>");
    }

    @Test
    void aCheckThatCannotRunNeverBlocksTheAnswer() {
        workspace.failing = true;

        GenerationResult result = service(2).repair(generator, request(), first(BROKEN), listener);

        assertThat(result.fullText()).isEqualTo(BROKEN);
        assertThat(generator.requests).isEmpty();
    }

    @Test
    void anAnswerWithoutFilesAndASwitchedOffRepairAreNotChecked() {
        service(2).repair(generator, request(), first("<message>Just an explanation.</message>"), listener);
        service(0).repair(generator, request(), first(BROKEN), listener);

        assertThat(workspace.checked).isEmpty();
    }

    @Test
    void theErrorSummaryNamesTheFileAndLine() {
        assertThat(BuildRepairService.summary(VITE_ERROR)).isEqualTo("src/App.tsx:1:50: ERROR: Expected \">\" but found \"}\"");
        assertThat(BuildRepairService.summary("error during build:\nsomething odd")).isEqualTo("something odd");
    }

    @Test
    void aPluginErrorIsSummarisedByItsMessageNotByTheBuildToolsStackFrames() {
        String tailwind = """
                ✗ Build failed in 186ms
                error during build:
                [@tailwindcss/vite:generate:build] Cannot apply unknown utility class `bg-coffee-500`
                file: src/index.css
                    at Object.handler (file:///node_modules/vite/dist/node/chunks/dep-Cohbwjf9.js:46861:15)""";

        assertThat(BuildRepairService.summary(tailwind))
                .isEqualTo("[@tailwindcss/vite:generate:build] Cannot apply unknown utility class 'bg-coffee-500'");
    }

    private BuildRepairService service(int rounds) {
        return new BuildRepairService(workspace, parser, rounds);
    }

    private static GenerationRequest request() {
        return new GenerationRequest(7L, "A coffee shop site", List.of(),
                List.of(new FileNode("package.json", 10L, null)), path -> "saved " + path);
    }

    private static GenerationResult first(String text) {
        return new GenerationResult(text, 20, 10, "main-model");
    }

    private static final class ScriptedGenerator implements CodeGenerator {
        final Deque<String> replies = new ArrayDeque<>();
        final List<GenerationRequest> requests = new ArrayList<>();

        @Override
        public GenerationResult generate(GenerationRequest request, ReplyListener listener) {
            requests.add(request);
            String reply = replies.remove();
            listener.delta(reply);
            return new GenerationResult(reply, 20, 10, "main-model");
        }
    }

    private static final class FakeWorkspace implements WorkspaceClient {
        final Deque<BuildCheckResult> results = new ArrayDeque<>();
        final List<Map<String, String>> checked = new ArrayList<>();
        boolean failing;

        @Override
        public BuildCheckResult checkBuild(Long projectId, BuildCheckRequest request) {
            if (failing) {
                throw new IllegalStateException("workspace-service is not reachable");
            }
            checked.add(request.files());
            return results.remove();
        }

        @Override
        public FileTreeDto getFileTree(Long projectId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getFileContent(Long projectId, String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean checkPermission(Long projectId, ProjectPermission permission) {
            throw new UnsupportedOperationException();
        }
    }
}
