package com.springproject.lovable_clone.intelligence.llm;

import com.springproject.lovable_clone.common.dto.FileNode;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Produces a tagged reply (see PromptUtils) for one user request, streaming text as it goes.
 */
public interface CodeGenerator {

    GenerationResult generate(GenerationRequest request, Consumer<String> onDelta);

    /**
     * @param history    earlier turns, oldest first (role "user"/"assistant", text only)
     * @param readFile   reads a project file by path (used by the read_files tool)
     */
    record GenerationRequest(
            Long projectId,
            String userMessage,
            List<HistoryTurn> history,
            List<FileNode> files,
            Function<String, String> readFile
    ) {}

    record HistoryTurn(boolean fromUser, String text) {}

    record GenerationResult(String fullText, long inputTokens, long outputTokens, String model) {
        public long totalTokens() {
            return inputTokens + outputTokens;
        }
    }
}
