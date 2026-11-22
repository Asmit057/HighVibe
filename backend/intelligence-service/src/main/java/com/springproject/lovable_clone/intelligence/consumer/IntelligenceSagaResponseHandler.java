package com.springproject.lovable_clone.intelligence.consumer;

import com.springproject.lovable_clone.common.enums.ChatEventStatus;
import com.springproject.lovable_clone.common.event.FileStoreResponseEvent;
import com.springproject.lovable_clone.common.event.KafkaTopics;
import com.springproject.lovable_clone.intelligence.repository.ChatEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga completion: marks each FILE_EDIT event CONFIRMED or FAILED once workspace-service replies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntelligenceSagaResponseHandler {

    private final ChatEventRepository chatEventRepository;

    @Transactional
    @KafkaListener(topics = KafkaTopics.FILE_STORE_RESPONSES, groupId = "intelligence-group")
    public void handle(FileStoreResponseEvent response) {
        chatEventRepository.findBySagaId(response.sagaId()).ifPresentOrElse(event -> {
            if (event.getStatus() != ChatEventStatus.PENDING) {
                return; // duplicate delivery
            }
            event.setStatus(response.success() ? ChatEventStatus.CONFIRMED : ChatEventStatus.FAILED);
            if (!response.success()) {
                event.setMetadata(response.errorMessage());
                log.warn("Saga {} failed for {}: {}", response.sagaId(), event.getFilePath(), response.errorMessage());
            }
        }, () -> log.warn("No chat event for saga {}", response.sagaId()));
    }
}
