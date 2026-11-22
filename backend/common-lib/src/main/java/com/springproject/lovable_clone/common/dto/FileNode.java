package com.springproject.lovable_clone.common.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * One file in a project's tree. {@link #toString()} is just the path, so a list of nodes
 * renders as a compact file listing inside LLM prompts.
 */
public record FileNode(
        String path,
        Long size,
        Instant modifiedAt
) implements Serializable {
    @Override
    public String toString() {
        return path;
    }
}
