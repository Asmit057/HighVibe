package com.springproject.lovable_clone.intelligence.entity;

import com.springproject.lovable_clone.common.enums.MessageRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_messages_session", columnList = "chat_session_id, id"))
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_session_id", nullable = false)
    ChatSession chatSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    MessageRole role;

    // user prompt, or the assistant's full tagged response
    @Column(columnDefinition = "text")
    String content;

    Integer tokensUsed;

    @Builder.Default
    @OneToMany(mappedBy = "chatMessage", fetch = FetchType.LAZY)
    @OrderBy("sequenceOrder ASC")
    List<ChatEvent> events = new ArrayList<>();

    @CreationTimestamp
    Instant createdAt;
}
