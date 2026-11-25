package com.springproject.lovable_clone.intelligence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.springproject.lovable_clone.common.enums.ChatEventStatus;
import com.springproject.lovable_clone.common.enums.ChatEventType;
import com.springproject.lovable_clone.common.enums.MessageRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Request, response and SSE payloads of intelligence-service.
 */
public final class ChatDtos {

    private ChatDtos() {}

    public record ChatRequest(
            @NotBlank @Size(max = 8000) String message,
            @NotNull Long projectId
    ) {}

    public record ChatEventResponse(
            Long id,
            ChatEventType type,
            Integer sequenceOrder,
            String content,
            String filePath,
            String metadata,
            ChatEventStatus status
    ) {}

    public record ChatResponse(
            Long id,
            MessageRole role,
            List<ChatEventResponse> events,
            String content,
            Integer tokensUsed,
            Instant createdAt
    ) {}

    /**
     * One server-sent event of a generation:
     * "delta" (streamed text), "done" (final assistant message), or "error".
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StreamEvent(String type, String text, ChatResponse message, String error) {
        public static StreamEvent delta(String text) {
            return new StreamEvent("delta", text, null, null);
        }

        /** The text streamed so far is discarded; the model starts the answer again. */
        public static StreamEvent restart() {
            return new StreamEvent("restart", null, null, null);
        }

        public static StreamEvent done(ChatResponse message) {
            return new StreamEvent("done", null, message, null);
        }

        public static StreamEvent error(String error) {
            return new StreamEvent("error", null, null, error);
        }
    }

    public record UsageTodayResponse(
            String planName,
            long tokensUsed,
            Integer tokenLimit,
            boolean unlimited,
            int generations
    ) {}
}
