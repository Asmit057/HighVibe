package com.springproject.lovable_clone.workspace.consumer;

import com.springproject.lovable_clone.common.event.FileStoreRequestEvent;
import com.springproject.lovable_clone.common.event.FileStoreResponseEvent;
import com.springproject.lovable_clone.common.event.KafkaTopics;
import com.springproject.lovable_clone.workspace.entity.ProcessedEvent;
import com.springproject.lovable_clone.workspace.repository.ProcessedEventRepository;
import com.springproject.lovable_clone.workspace.service.ProjectFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * Saga participant: stores AI-generated files requested by intelligence-service and replies
 * with the outcome. Redelivered requests (same saga id) are acknowledged without re-applying.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageConsumer {

    private final ProjectFileService projectFileService;
    private final ProcessedEventRepository processedEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionTemplate tx;

    @KafkaListener(topics = KafkaTopics.FILE_STORE_REQUESTS, groupId = "workspace-group")
    public void consume(FileStoreRequestEvent request) {
        if (processedEventRepository.existsById(request.sagaId())) {
            log.info("Saga {} already applied, re-sending ACK", request.sagaId());
            reply(request, true, null);
            return;
        }
        try {
            tx.executeWithoutResult(status -> {
                projectFileService.saveFile(request.projectId(), request.filePath(), request.content(), request.userId());
                processedEventRepository.save(new ProcessedEvent(request.sagaId(), Instant.now()));
            });
            log.info("Saga {}: saved {} in project {}", request.sagaId(), request.filePath(), request.projectId());
            reply(request, true, null);
        } catch (Exception e) {
            log.error("Saga {}: could not save {}: {}", request.sagaId(), request.filePath(), e.getMessage());
            reply(request, false, e.getMessage());
        }
    }

    private void reply(FileStoreRequestEvent request, boolean success, String error) {
        kafkaTemplate.send(KafkaTopics.FILE_STORE_RESPONSES, "project-" + request.projectId(),
                FileStoreResponseEvent.builder()
                        .sagaId(request.sagaId())
                        .projectId(request.projectId())
                        .success(success)
                        .errorMessage(error)
                        .build());
    }
}
