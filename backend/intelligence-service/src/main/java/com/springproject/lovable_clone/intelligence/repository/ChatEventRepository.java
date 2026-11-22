package com.springproject.lovable_clone.intelligence.repository;

import com.springproject.lovable_clone.intelligence.entity.ChatEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatEventRepository extends JpaRepository<ChatEvent, Long> {
    Optional<ChatEvent> findBySagaId(String sagaId);
}
