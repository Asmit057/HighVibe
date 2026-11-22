package com.springproject.lovable_clone.workspace.service.preview;

import com.springproject.lovable_clone.common.dto.PlanDto;
import com.springproject.lovable_clone.common.enums.PreviewStatus;
import com.springproject.lovable_clone.common.enums.ProjectPermission;
import com.springproject.lovable_clone.common.error.ResourceNotFoundException;
import com.springproject.lovable_clone.common.error.UsageLimitExceededException;
import com.springproject.lovable_clone.common.security.AuthUtil;
import com.springproject.lovable_clone.workspace.client.AccountClient;
import com.springproject.lovable_clone.workspace.config.WorkspaceProperties;
import com.springproject.lovable_clone.workspace.dto.WorkspaceDtos.PreviewResponse;
import com.springproject.lovable_clone.workspace.entity.Preview;
import com.springproject.lovable_clone.workspace.repository.PreviewRepository;
import com.springproject.lovable_clone.workspace.security.ProjectAccessService;
import com.springproject.lovable_clone.workspace.service.FileChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Preview lifecycle: CREATING -> RUNNING -> TERMINATED (or FAILED). Starting is asynchronous;
 * clients poll GET /api/projects/{id}/preview until the status is RUNNING.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewService {

    private static final Set<PreviewStatus> ACTIVE = EnumSet.of(PreviewStatus.CREATING, PreviewStatus.RUNNING);

    private final PreviewRepository previewRepository;
    private final PreviewRunner runner;
    private final ProjectAccessService access;
    private final AccountClient accountClient;
    private final AuthUtil authUtil;
    private final WorkspaceProperties properties;
    private final TransactionTemplate tx;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Long> restarting = ConcurrentHashMap.newKeySet();

    public PreviewResponse deploy(Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        access.require(projectId, userId, ProjectPermission.VIEW);

        Preview active = previewRepository.findFirstByProjectIdAndStatusInOrderByCreatedAtDesc(projectId, ACTIVE).orElse(null);
        if (active != null && (active.getStatus() == PreviewStatus.CREATING || runner.isRunning(projectId))) {
            active.setExpiresAt(Instant.now().plus(properties.preview().ttl()));
            return toResponse(previewRepository.save(active));
        }
        if (active != null) {
            markTerminated(active.getId(), "Dev server stopped");
        }

        PlanDto plan = accountClient.getCurrentPlan();
        long running = previewRepository.countByUserIdAndStatusIn(userId, ACTIVE);
        if (plan.maxPreviews() != null && running >= plan.maxPreviews()) {
            throw new UsageLimitExceededException("Your " + plan.name() + " plan allows " + plan.maxPreviews()
                    + " live preview(s) at a time. Stop another preview or upgrade.");
        }

        Preview preview = previewRepository.save(Preview.builder()
                .projectId(projectId)
                .userId(userId)
                .status(PreviewStatus.CREATING)
                .expiresAt(Instant.now().plus(properties.preview().ttl()))
                .build());
        Long previewId = preview.getId();
        executor.submit(() -> boot(previewId, projectId));
        return toResponse(preview);
    }

    public PreviewResponse getLatest(Long projectId) {
        access.require(projectId, ProjectPermission.VIEW);
        Preview preview = previewRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Preview", "project " + projectId));
        if (preview.getStatus() == PreviewStatus.RUNNING && !runner.isRunning(projectId)) {
            markTerminated(preview.getId(), "Dev server stopped");
            preview = previewRepository.findById(preview.getId()).orElseThrow();
        }
        return toResponse(preview);
    }

    public void stop(Long projectId) {
        access.require(projectId, ProjectPermission.VIEW);
        stopAllForProject(projectId);
    }

    public void stopAllForProject(Long projectId) {
        runner.stop(projectId);
        previewRepository.findFirstByProjectIdAndStatusInOrderByCreatedAtDesc(projectId, ACTIVE)
                .ifPresent(p -> markTerminated(p.getId(), null));
    }

    /** Hot-reload: push saved files into the running preview (after the save commits). */
    @TransactionalEventListener(fallbackExecution = true)
    public void onFileChanged(FileChangedEvent event) {
        if (runner.onFileChanged(event) && restarting.add(event.projectId())) {
            // package.json changed: reinstall dependencies and restart the dev server
            executor.submit(() -> {
                try {
                    previewRepository.findFirstByProjectIdAndStatusInOrderByCreatedAtDesc(event.projectId(), ACTIVE)
                            .ifPresent(p -> boot(p.getId(), event.projectId()));
                } finally {
                    restarting.remove(event.projectId());
                }
            });
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expirePreviews() {
        for (Preview preview : previewRepository.findByStatusInAndExpiresAtBefore(ACTIVE, Instant.now())) {
            log.info("Preview {} of project {} expired", preview.getId(), preview.getProjectId());
            runner.stop(preview.getProjectId());
            markTerminated(preview.getId(), null);
        }
    }

    /** Local dev servers do not survive a restart of this service. */
    @EventListener(ApplicationReadyEvent.class)
    public void cleanupStalePreviews() {
        if (properties.preview().isKubernetes()) {
            return;
        }
        previewRepository.findByStatusIn(ACTIVE).forEach(p -> markTerminated(p.getId(), "Service restarted"));
    }

    private void boot(Long previewId, Long projectId) {
        try {
            PreviewRunner.StartedPreview started = runner.start(projectId);
            tx.executeWithoutResult(status -> previewRepository.findById(previewId).ifPresent(p -> {
                p.setStatus(PreviewStatus.RUNNING);
                p.setPreviewUrl(started.url());
                p.setPort(started.port());
                p.setPodName(started.podName());
                p.setStartedAt(Instant.now());
                p.setFailureReason(null);
                previewRepository.save(p);
            }));
        } catch (Exception e) {
            log.warn("Preview of project {} failed: {}", projectId, e.getMessage());
            tx.executeWithoutResult(status -> previewRepository.findById(previewId).ifPresent(p -> {
                p.setStatus(PreviewStatus.FAILED);
                p.setFailureReason(truncate(e.getMessage()));
                p.setTerminatedAt(Instant.now());
                previewRepository.save(p);
            }));
        }
    }

    private void markTerminated(Long previewId, String reason) {
        tx.executeWithoutResult(status -> previewRepository.findById(previewId).ifPresent(p -> {
            p.setStatus(PreviewStatus.TERMINATED);
            p.setTerminatedAt(Instant.now());
            if (reason != null) p.setFailureReason(reason);
            previewRepository.save(p);
        }));
    }

    private static String truncate(String message) {
        if (message == null) return "Unknown error";
        return message.length() > 1900 ? message.substring(message.length() - 1900) : message;
    }

    private static PreviewResponse toResponse(Preview p) {
        return new PreviewResponse(p.getId(), p.getProjectId(), p.getStatus(), p.getPreviewUrl(),
                p.getFailureReason(), p.getStartedAt(), p.getExpiresAt());
    }
}
