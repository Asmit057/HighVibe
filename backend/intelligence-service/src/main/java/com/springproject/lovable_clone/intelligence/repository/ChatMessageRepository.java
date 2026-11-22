package com.springproject.lovable_clone.intelligence.repository;

import com.springproject.lovable_clone.intelligence.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT DISTINCT m FROM ChatMessage m LEFT JOIN FETCH m.events WHERE m.chatSession.id = :sessionId ORDER BY m.id ASC")
    List<ChatMessage> findWithEventsBySessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT m FROM ChatMessage m WHERE m.chatSession.id = :sessionId ORDER BY m.id DESC")
    List<ChatMessage> findLatest(@Param("sessionId") Long sessionId, Pageable pageable);
}
