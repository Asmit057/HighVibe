package com.springproject.lovable_clone.workspace.service.preview;

import com.springproject.lovable_clone.workspace.service.FileChangedEvent;

/**
 * Starts and stops the dev server that serves a project's live preview.
 */
public interface PreviewRunner {

    /** Blocks until the preview answers HTTP, or throws with a readable reason. */
    StartedPreview start(Long projectId);

    void stop(Long projectId);

    /** Applies a saved file to a running preview. Returns true when the preview must restart. */
    boolean onFileChanged(FileChangedEvent event);

    boolean isRunning(Long projectId);

    record StartedPreview(String url, Integer port, String podName) {}
}
