package com.springproject.lovable_clone.workspace.service.preview;

import com.springproject.lovable_clone.workspace.config.WorkspaceProperties;
import com.springproject.lovable_clone.workspace.service.FileChangedEvent;
import com.springproject.lovable_clone.workspace.service.FilePaths;
import com.springproject.lovable_clone.workspace.service.ProjectFileService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Runs each preview as a Vite dev server on this machine (needs Node.js on the PATH).
 * Files are materialised under {app.preview.local.root}/{projectId}; later saves are written
 * straight to disk so Vite hot-reloads them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.preview.mode", havingValue = "local", matchIfMissing = true)
public class LocalPreviewRunner implements PreviewRunner {

    private final WorkspaceProperties properties;
    private final ProjectFileService projectFileService;
    private final Map<Long, RunningPreview> running = new ConcurrentHashMap<>();
    // serialises "copy the whole project" with "apply one saved file" for the same project
    // (a ReentrantLock, not synchronized: Java 21 virtual threads would be pinned during I/O)
    private final Map<Long, ReentrantLock> folderLocks = new ConcurrentHashMap<>();

    @Override
    public StartedPreview start(Long projectId) {
        stop(projectId);
        Path dir = projectDir(projectId);
        ReentrantLock lock = folderLock(projectId);
        lock.lock();
        try {
            // loading inside the lock: a file saved meanwhile is either in this snapshot or
            // applied right after by onFileChanged, never deleted by the clean-up below
            NodeProjects.materialize(dir, projectFileService.loadAllFiles(projectId));
        } finally {
            lock.unlock();
        }
        NodeProjects.installDependencies(dir, metaDir(projectId));

        int port = freePort();
        var local = properties.preview().local();
        String host = local.host();
        // logs live outside the project folder: Vite reloads the page on any change it watches
        Path devLog = metaDir(projectId).resolve("dev.log");
        Process process = NodeProjects.launch(dir, devLog, "npm", "run", "dev", "--", "--host", host, "--port", String.valueOf(port), "--strictPort");
        running.put(projectId, new RunningPreview(process, port));

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(90);
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                running.remove(projectId);
                throw new IllegalStateException("Dev server exited: " + NodeProjects.tail(devLog));
            }
            if (portOpen(local.probeHost(), port)) {
                log.info("Preview of project {} running on port {}", projectId, port);
                return new StartedPreview("http://" + local.urlHost() + ":" + port, port, null);
            }
            sleep(500);
        }
        stop(projectId);
        throw new IllegalStateException("Dev server did not start within 90s: " + NodeProjects.tail(devLog));
    }

    @Override
    public void stop(Long projectId) {
        RunningPreview preview = running.remove(projectId);
        if (preview != null) {
            NodeProjects.kill(preview.process());
            log.info("Stopped preview of project {}", projectId);
        }
    }

    @Override
    public boolean isRunning(Long projectId) {
        RunningPreview preview = running.get(projectId);
        return preview != null && preview.process().isAlive();
    }

    @Override
    public boolean onFileChanged(FileChangedEvent event) {
        Path dir = projectDir(event.projectId());
        ReentrantLock lock = folderLock(event.projectId());
        lock.lock();
        try {
            if (!Files.isDirectory(dir)) {
                return false;
            }
            try {
                Path target = dir.resolve(FilePaths.normalize(event.path())).normalize();
                if (!target.startsWith(dir)) {
                    return false;
                }
                if (event.deleted()) {
                    Files.deleteIfExists(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.write(target, event.content());
                }
            } catch (IOException e) {
                log.warn("Could not sync {} to preview of project {}: {}", event.path(), event.projectId(), e.getMessage());
            }
        } finally {
            lock.unlock();
        }
        return "package.json".equals(event.path()) && isRunning(event.projectId());
    }

    private ReentrantLock folderLock(Long projectId) {
        return folderLocks.computeIfAbsent(projectId, id -> new ReentrantLock());
    }

    @PreDestroy
    public void shutdown() {
        new ArrayList<>(running.keySet()).forEach(this::stop);
    }

    // ---------------------------------------------------------------- helpers

    private Path projectDir(Long projectId) {
        return Paths.get(properties.preview().local().root()).toAbsolutePath().normalize().resolve(projectId.toString());
    }

    /** Logs and the dependency marker of a preview, kept outside the folder Vite watches. */
    private Path metaDir(Long projectId) {
        Path meta = Paths.get(properties.preview().local().root()).toAbsolutePath().normalize()
                .resolve(".meta").resolve(projectId.toString());
        try {
            Files.createDirectories(meta);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return meta;
    }

    private int freePort() {
        var local = properties.preview().local();
        Set<Integer> used = new HashSet<>();
        running.values().forEach(r -> used.add(r.port()));
        for (int port = local.portRangeStart(); port <= local.portRangeEnd(); port++) {
            if (!used.contains(port) && !portOpen(local.probeHost(), port)) {
                return port;
            }
        }
        throw new IllegalStateException("No free preview port between " + local.portRangeStart() + " and " + local.portRangeEnd());
    }

    private static boolean portOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 300);
            return true;
        } catch (IOException e) {
            return false;
        }
    }




    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record RunningPreview(Process process, int port) {}
}
