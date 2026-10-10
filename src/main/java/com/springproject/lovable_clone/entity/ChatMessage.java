package com.springproject.lovable_clone.entity;

import com.springproject.lovable_clone.enums.MessageRole;

import java.time.Instant;

public class ChatMessage {
    Long id;
    ChatSession chatSession;
    String content;
    String toolCalls;
    Integer tokensUsed;

    MessageRole role;
    Instant createdAt;

}
