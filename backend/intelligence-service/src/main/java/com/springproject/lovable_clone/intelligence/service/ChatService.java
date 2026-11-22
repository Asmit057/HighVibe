package com.springproject.lovable_clone.intelligence.service;

import com.springproject.lovable_clone.common.enums.ProjectPermission;
import com.springproject.lovable_clone.intelligence.client.WorkspaceClient;
import com.springproject.lovable_clone.intelligence.dto.ChatDtos.ChatEventResponse;
import com.springproject.lovable_clone.intelligence.dto.ChatDtos.ChatResponse;
import com.springproject.lovable_clone.intelligence.entity.ChatEvent;
import com.springproject.lovable_clone.intelligence.entity.ChatMessage;
import com.springproject.lovable_clone.intelligence.repository.ChatMessageRepository;
import com.springproject.lovable_clone.intelligence.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final WorkspaceClient workspaceClient;

    @Transactional(readOnly = true)
    public List<ChatResponse> getProjectChatHistory(Long projectId, Long userId) {
        if (!workspaceClient.checkPermission(projectId, ProjectPermission.VIEW)) {
            throw new AccessDeniedException("No access to project " + projectId);
        }
        return sessionRepository.findByProjectIdAndUserId(projectId, userId)
                .map(session -> messageRepository.findWithEventsBySessionId(session.getId()).stream()
                        .map(ChatService::toResponse)
                        .toList())
                .orElse(List.of());
    }

    public static ChatResponse toResponse(ChatMessage message) {
        List<ChatEventResponse> events = message.getEvents().stream()
                .sorted(Comparator.comparing(ChatEvent::getSequenceOrder))
                .map(e -> new ChatEventResponse(e.getId(), e.getType(), e.getSequenceOrder(), e.getContent(),
                        e.getFilePath(), e.getMetadata(), e.getStatus()))
                .toList();
        return new ChatResponse(message.getId(), message.getRole(), events, message.getContent(),
                message.getTokensUsed(), message.getCreatedAt());
    }
}
