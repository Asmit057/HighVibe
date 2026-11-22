package com.springproject.lovable_clone.intelligence.repository;

import com.springproject.lovable_clone.intelligence.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findByProjectIdAndUserId(Long projectId, Long userId);
}
