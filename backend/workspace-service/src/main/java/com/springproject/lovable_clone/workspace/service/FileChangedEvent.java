package com.springproject.lovable_clone.workspace.service;

/**
 * Published after a project file is written or deleted, so live previews can hot-reload.
 * {@code content} is null for deletions.
 */
public record FileChangedEvent(Long projectId, String path, byte[] content) {
    public boolean deleted() {
        return content == null;
    }
}
