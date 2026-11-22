package com.springproject.lovable_clone.intelligence.controller;

import com.springproject.lovable_clone.common.security.AuthUtil;
import com.springproject.lovable_clone.intelligence.dto.ChatDtos.ChatRequest;
import com.springproject.lovable_clone.intelligence.dto.ChatDtos.ChatResponse;
import com.springproject.lovable_clone.intelligence.dto.ChatDtos.UsageTodayResponse;
import com.springproject.lovable_clone.intelligence.service.AiGenerationService;
import com.springproject.lovable_clone.intelligence.service.ChatService;
import com.springproject.lovable_clone.intelligence.service.UsageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final AiGenerationService aiGenerationService;
    private final ChatService chatService;
    private final UsageService usageService;
    private final AuthUtil authUtil;

    /** Streams one AI turn as server-sent events (delta / done / error). */
    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody @Valid ChatRequest request) {
        return aiGenerationService.streamResponse(request);
    }

    @GetMapping("/api/chat/projects/{projectId}")
    public ResponseEntity<List<ChatResponse>> history(@PathVariable Long projectId) {
        return ResponseEntity.ok(chatService.getProjectChatHistory(projectId, authUtil.getCurrentUserId()));
    }

    @GetMapping("/api/usage/today")
    public ResponseEntity<UsageTodayResponse> usageToday() {
        return ResponseEntity.ok(usageService.getTodayUsage(authUtil.getCurrentUserId()));
    }
}
