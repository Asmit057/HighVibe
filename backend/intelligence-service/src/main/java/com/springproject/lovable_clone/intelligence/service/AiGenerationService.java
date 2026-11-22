package com.springproject.lovable_clone.intelligence.service;

import com.springproject.lovable_clone.common.dto.FileNode;
import com.springproject.lovable_clone.common.enums.ChatEventStatus;
import com.springproject.lovable_clone.common.enums.ChatEventType;
import com.springproject.lovable_clone.common.enums.MessageRole;
import com.springproject.lovable_clone.common.enums.ProjectPermission;
import com.springproject.lovable_clone.common.error.ConflictException;
import com.springproject.lovable_clone.common.event.FileStoreRequestEvent;
import com.springproject.lovable_clone.common.event.KafkaTopics;
import com.springproject.lovable_clone.common.security.AuthUtil;
import com.springproject.lovable_clone.intelligence.client.WorkspaceClient;
import com.springproject.lovable_clone.intelligence.config.AiProperties;
import com.springproject.lovable_clone.intelligence.dto.ChatDtos.ChatRequest;
import com.springproject.lovable_clone.intelligence.dto.ChatDtos.ChatResponse;
import com.springproject.lovable_clone.intelligence.dto.ChatDtos.StreamEvent;
import com.springproject.lovable_clone.intelligence.entity.ChatEvent;
import com.springproject.lovable_clone.intelligence.entity.ChatMessage;
import com.springproject.lovable_clone.intelligence.entity.ChatSession;
import com.springproject.lovable_clone.intelligence.llm.CodeGenerator;
import com.springproject.lovable_clone.intelligence.llm.CodeGenerator.GenerationRequest;
import com.springproject.lovable_clone.intelligence.llm.CodeGenerator.GenerationResult;
import com.springproject.lovable_clone.intelligence.llm.CodeGenerator.HistoryTurn;
import com.springproject.lovable_clone.intelligence.llm.DemoCodeGenerator;
import com.springproject.lovable_clone.intelligence.llm.GeminiCodeGenerator;
import com.springproject.lovable_clone.intelligence.llm.LlmResponseParser;
import com.springproject.lovable_clone.intelligence.repository.ChatEventRepository;
import com.springproject.lovable_clone.intelligence.repository.ChatMessageRepository;
import com.springproject.lovable_clone.intelligence.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Orchestrates one AI turn:
 * <ol>
 *   <li>check EDIT permission (workspace-service) and the daily token budget (account plan);</li>
 *   <li>stream the model reply to the browser over SSE;</li>
 *   <li>persist the reply as ordered chat events;</li>
 *   <li>start the file-save saga: one Kafka request per generated file, confirmed asynchronously.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGenerationService {

    private static final long SSE_TIMEOUT_MS = 20 * 60 * 1000L;
    private static final Pattern FILE_BODY = Pattern.compile("(<file\\s+path=\"[^\"]*\"\\s*>)[\\s\\S]*?(</file>)");

    private final AiProperties aiProperties;
    private final GeminiCodeGenerator geminiGenerator;
    private final DemoCodeGenerator demoGenerator;
    private final LlmResponseParser parser;
    private final WorkspaceClient workspaceClient;
    private final UsageService usageService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatEventRepository eventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionTemplate tx;
    private final AuthUtil authUtil;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Long> busyProjects = ConcurrentHashMap.newKeySet();

    public SseEmitter streamResponse(ChatRequest request) {
        Long userId = authUtil.getCurrentUserId();
        Long projectId = request.projectId();

        if (!workspaceClient.checkPermission(projectId, ProjectPermission.EDIT)) {
            throw new AccessDeniedException("You need edit access to change project " + projectId);
        }
        usageService.checkDailyTokens(userId);
        if (!busyProjects.add(projectId)) {
            throw new ConflictException("A generation is already running for this project");
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean clientGone = new AtomicBoolean(false);
        emitter.onCompletion(() -> clientGone.set(true));
        emitter.onTimeout(() -> clientGone.set(true));
        emitter.onError(e -> clientGone.set(true));

        Runnable job = () -> {
            try {
                run(request, userId, emitter, clientGone);
            } finally {
                busyProjects.remove(projectId);
            }
        };
        // the worker needs the caller's JWT for Feign calls to workspace/account services
        executor.submit(DelegatingSecurityContextRunnable.create(job, SecurityContextHolder.getContext()));
        return emitter;
    }

    private void run(ChatRequest request, Long userId, SseEmitter emitter, AtomicBoolean clientGone) {
        Long projectId = request.projectId();
        try {
            Turn turn = tx.execute(status -> startTurn(projectId, userId, request.message()));
            List<FileNode> files = workspaceClient.getFileTree(projectId).files();
            CodeGenerator generator = switch (aiProperties.resolveProvider()) {
                case GEMINI -> geminiGenerator;
                case DEMO -> demoGenerator;
            };

            long started = System.currentTimeMillis();
            GenerationResult result = generator.generate(
                    new GenerationRequest(projectId, request.message(), Objects.requireNonNull(turn).history(), files,
                            path -> workspaceClient.getFileContent(projectId, path)),
                    text -> send(emitter, clientGone, StreamEvent.delta(text)));
            long seconds = Math.max(1, (System.currentTimeMillis() - started) / 1000);

            usageService.recordTokens(userId, result.totalTokens());
            ChatResponse response = finishTurn(turn.sessionId(), projectId, userId, result, seconds);
            send(emitter, clientGone, StreamEvent.done(response));
            emitter.complete();
            log.info("Generation for project {} finished with {} ({} tokens, {}s)",
                    projectId, result.model(), result.totalTokens(), seconds);
        } catch (Exception e) {
            log.error("Generation failed for project {}", projectId, e);
            send(emitter, clientGone, StreamEvent.error(userFacing(e)));
            emitter.complete();
        }
    }

    private Turn startTurn(Long projectId, Long userId, String message) {
        ChatSession session = sessionRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseGet(() -> sessionRepository.save(ChatSession.builder().projectId(projectId).userId(userId).build()));

        List<ChatMessage> latest = new ArrayList<>(messageRepository.findLatest(session.getId(),
                PageRequest.of(0, Math.max(0, aiProperties.historyMessages()))));
        Collections.reverse(latest);
        List<HistoryTurn> history = latest.stream()
                .filter(m -> m.getContent() != null && !m.getContent().isBlank())
                .map(m -> new HistoryTurn(m.getRole() == MessageRole.USER, compact(m)))
                .toList();

        messageRepository.save(ChatMessage.builder()
                .chatSession(session)
                .role(MessageRole.USER)
                .content(message)
                .build());
        return new Turn(session.getId(), history);
    }

    private ChatResponse finishTurn(Long sessionId, Long projectId, Long userId, GenerationResult result, long seconds) {
        List<FileStoreRequestEvent> saga = new ArrayList<>();
        ChatResponse response = tx.execute(status -> {
            ChatMessage assistant = messageRepository.save(ChatMessage.builder()
                    .chatSession(sessionRepository.getReferenceById(sessionId))
                    .role(MessageRole.ASSISTANT)
                    .content(result.fullText())
                    .tokensUsed((int) Math.min(Integer.MAX_VALUE, result.totalTokens()))
                    .build());

            List<ChatEvent> events = new ArrayList<>();
            events.add(ChatEvent.builder()
                    .chatMessage(assistant)
                    .type(ChatEventType.THOUGHT)
                    .status(ChatEventStatus.CONFIRMED)
                    .sequenceOrder(0)
                    .content("Worked for " + seconds + "s")
                    .build());

            int order = 1;
            for (LlmResponseParser.ParsedEvent parsed : parser.parse(result.fullText())) {
                ChatEvent.ChatEventBuilder event = ChatEvent.builder()
                        .chatMessage(assistant)
                        .type(parsed.type())
                        .sequenceOrder(order++)
                        .content(parsed.content())
                        .filePath(parsed.filePath())
                        .metadata(parsed.metadata())
                        .status(ChatEventStatus.CONFIRMED);
                if (parsed.type() == ChatEventType.FILE_EDIT) {
                    String sagaId = UUID.randomUUID().toString();
                    event.status(ChatEventStatus.PENDING).sagaId(sagaId);
                    saga.add(new FileStoreRequestEvent(projectId, sagaId, parsed.filePath(), parsed.content(), userId));
                }
                events.add(event.build());
            }
            eventRepository.saveAll(events);
            assistant.getEvents().addAll(events);
            return ChatService.toResponse(assistant);
        });

        // publish after commit, so the saga replies always find their PENDING events
        for (FileStoreRequestEvent request : saga) {
            kafkaTemplate.send(KafkaTopics.FILE_STORE_REQUESTS, "project-" + projectId, request);
        }
        return response;
    }

    /** Earlier assistant replies are sent back without file bodies; the model can re-read files. */
    private static String compact(ChatMessage message) {
        if (message.getRole() != MessageRole.ASSISTANT) {
            return message.getContent();
        }
        return FILE_BODY.matcher(message.getContent()).replaceAll("$1(file written)$2");
    }

    private static void send(SseEmitter emitter, AtomicBoolean clientGone, StreamEvent event) {
        if (clientGone.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().data(event, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            clientGone.set(true);
        }
    }

    private static String userFacing(Exception e) {
        String message = e.getMessage();
        return "Generation failed: " + (message == null ? e.getClass().getSimpleName() : message);
    }

    private record Turn(Long sessionId, List<HistoryTurn> history) {}
}
