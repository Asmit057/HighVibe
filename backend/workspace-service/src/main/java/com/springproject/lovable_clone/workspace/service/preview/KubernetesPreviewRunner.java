package com.springproject.lovable_clone.workspace.service.preview;

import com.springproject.lovable_clone.workspace.config.WorkspaceProperties;
import com.springproject.lovable_clone.workspace.service.FileChangedEvent;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Production previews: claims an idle pod from the runner pool (k8s/infra/runner-pool.yaml),
 * mirrors the project from MinIO into it, starts Vite and registers the route
 * "project-{id}.{domain}" in Redis for the wildcard preview proxy (k8s/proxy).
 * The syncer container keeps "mc mirror --watch" running, so later saves reach the pod.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.preview.mode", havingValue = "kubernetes")
public class KubernetesPreviewRunner implements PreviewRunner {

    private static final String POOL_LABEL = "status";
    private static final String PROJECT_LABEL = "project-id";
    private static final String IDLE = "idle";
    private static final String BUSY = "busy";

    private final KubernetesClient client;
    private final StringRedisTemplate redisTemplate;
    private final WorkspaceProperties properties;

    @Override
    public StartedPreview start(Long projectId) {
        var k8s = properties.preview().kubernetes();
        String domain = "project-" + projectId + "." + k8s.domain();
        String url = "80".equals(k8s.proxyPort()) ? "http://" + domain : "http://" + domain + ":" + k8s.proxyPort();

        Pod existing = findActivePod(projectId);
        if (existing != null) {
            registerRoute(domain, existing);
            return new StartedPreview(url, 5173, existing.getMetadata().getName());
        }

        Pod pod = client.pods().inNamespace(k8s.namespace()).withLabel(POOL_LABEL, IDLE).list().getItems().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No idle preview runners available, scale up the runner-pool"));
        String podName = pod.getMetadata().getName();

        client.pods().inNamespace(k8s.namespace()).withName(podName).edit(p -> {
            p.getMetadata().getLabels().put(POOL_LABEL, BUSY);
            p.getMetadata().getLabels().put(PROJECT_LABEL, projectId.toString());
            return p;
        });

        try {
            exec(podName, "syncer", String.format("rm -rf /app/* && mc mirror --overwrite myminio/projects/%d/ /app/", projectId));
            exec(podName, "syncer", String.format(
                    "nohup mc mirror --overwrite --watch myminio/projects/%d/ /app/ > /tmp/sync.log 2>&1 &", projectId));
            exec(podName, "runner", "npm install && nohup npm run dev -- --host 0.0.0.0 --port 5173 > /tmp/dev.log 2>&1 &");
            registerRoute(domain, client.pods().inNamespace(k8s.namespace()).withName(podName).get());
            return new StartedPreview(url, 5173, podName);
        } catch (RuntimeException e) {
            // a half-initialised pod is discarded; the Deployment replaces it with a clean idle runner
            client.pods().inNamespace(k8s.namespace()).withName(podName).delete();
            throw e;
        }
    }

    @Override
    public void stop(Long projectId) {
        var k8s = properties.preview().kubernetes();
        redisTemplate.delete("route:project-" + projectId + "." + k8s.domain());
        client.pods().inNamespace(k8s.namespace()).withLabel(PROJECT_LABEL, projectId.toString()).delete();
    }

    @Override
    public boolean onFileChanged(FileChangedEvent event) {
        // the syncer container mirrors MinIO changes into the pod
        return false;
    }

    @Override
    public boolean isRunning(Long projectId) {
        return findActivePod(projectId) != null;
    }

    private Pod findActivePod(Long projectId) {
        return client.pods().inNamespace(properties.preview().kubernetes().namespace())
                .withLabel(PROJECT_LABEL, projectId.toString())
                .withLabel(POOL_LABEL, BUSY)
                .list().getItems().stream()
                .filter(pod -> "Running".equals(pod.getStatus().getPhase()))
                .findFirst()
                .orElse(null);
    }

    private void registerRoute(String domain, Pod pod) {
        String podIp = pod.getStatus().getPodIP();
        if (podIp == null) {
            throw new IllegalStateException("Runner pod has no IP yet");
        }
        redisTemplate.opsForValue().set("route:" + domain, podIp + ":5173",
                properties.preview().ttl().toMinutes() + 5, TimeUnit.MINUTES);
    }

    private void exec(String podName, String container, String script) {
        CompletableFuture<Integer> done = new CompletableFuture<>();
        try (ExecWatch ignored = client.pods().inNamespace(properties.preview().kubernetes().namespace())
                .withName(podName)
                .inContainer(container)
                .writingOutput(new ByteArrayOutputStream())
                .writingError(new ByteArrayOutputStream())
                .usingListener(new ExecListener() {
                    @Override
                    public void onClose(int code, String reason) {
                        done.complete(code);
                    }
                })
                .exec("sh", "-c", script)) {
            if (script.trim().endsWith("&")) {
                Thread.sleep(500);
            } else {
                done.get(120, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while preparing the preview pod");
        } catch (Exception e) {
            throw new IllegalStateException("Preview pod command failed: " + e.getMessage(), e);
        }
    }
}
