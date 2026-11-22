package com.springproject.lovable_clone.intelligence.entity;

import com.springproject.lovable_clone.common.enums.ChatEventStatus;
import com.springproject.lovable_clone.common.enums.ChatEventType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * One parsed step of an assistant reply: a message, a file read, or a file edit.
 * FILE_EDIT events stay PENDING until workspace-service confirms the save over Kafka.
 */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "chat_events", indexes = @Index(name = "idx_chat_events_saga", columnList = "saga_id"))
public class ChatEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_message_id", nullable = false)
    ChatMessage chatMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    ChatEventType type;

    @Column(nullable = false)
    Integer sequenceOrder;

    @Column(columnDefinition = "text")
    String content;

    @Column(length = 512)
    String filePath;

    @Column(columnDefinition = "text")
    String metadata;

    @Column(name = "saga_id", length = 64)
    String sagaId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    ChatEventStatus status;
}
