package com.springproject.lovable_clone.workspace.repository;

import com.springproject.lovable_clone.workspace.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
