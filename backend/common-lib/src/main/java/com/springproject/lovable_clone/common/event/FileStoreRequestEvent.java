package com.springproject.lovable_clone.common.event;

/**
 * Saga step 1 (intelligence -> workspace): persist one AI-generated file.
 */
public record FileStoreRequestEvent(
        Long projectId,
        String sagaId,
        String filePath,
        String content,
        Long userId
) {}
