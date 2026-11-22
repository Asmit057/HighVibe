package com.springproject.lovable_clone.common.event;

import lombok.Builder;

/**
 * Saga step 2 (workspace -> intelligence): outcome of a FileStoreRequestEvent.
 */
@Builder
public record FileStoreResponseEvent(
        String sagaId,
        boolean success,
        String errorMessage,
        Long projectId
) {}
