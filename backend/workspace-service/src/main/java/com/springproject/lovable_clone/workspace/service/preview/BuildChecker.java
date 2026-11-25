package com.springproject.lovable_clone.workspace.service.preview;

import com.springproject.lovable_clone.common.dto.BuildCheckResult;
import com.springproject.lovable_clone.workspace.config.WorkspaceProperties;
import com.springproject.lovable_clone.workspace.service.FilePaths;
import com.springproject.lovable_clone.workspace.service.ProjectFileService;
import com.springproject.lovable_clone.workspace.service.ProjectFileService.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Test-builds a project with Vite ({@code vite build}) with some files replaced, before AI-written files are saved.
 * A syntax error, a missing import or broken CSS is then found while the model can still fix it.
 *
 * Builds run in folders of their own (the preview folder is watched by the dev server and must only get final
 * files). Projects with the same package.json share a folder, so dependencies are installed once for all of them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuildChecker {

    private static final long BUILD_TIMEOUT_SECONDS = 120;
    private static final int MAX_OUTPUT_CHARS = 3000;
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[;\\d]*[A-Za-z]");
    // "    at failureErrorWithLog (node_modules/esbuild/...)": Node's stack frames say nothing about the app's code
    private static final Pattern STACK_FRAME = Pattern.compile("(?m)^\\s+at .*(\\R|$)");

    private final WorkspaceProperties properties;
    private final ProjectFileService projectFileService;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public BuildCheckResult check(Long projectId, Map<String, String> changedFiles) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (StoredFile file : projectFileService.loadAllFiles(projectId)) {
            files.put(file.path(), file.content());
        }
        changedFiles.forEach((path, content) -> files.put(FilePaths.normalize(path), content.getBytes(StandardCharsets.UTF_8)));
        byte[] packageJson = files.get("package.json");
        if (packageJson == null) {
            return BuildCheckResult.skipped("the project has no package.json");
        }

        String deps = NodeProjects.sha256(packageJson).substring(0, 12);
        Path root = Paths.get(properties.preview().local().root()).toAbsolutePath().normalize();
        Path dir = root.resolve(".check").resolve(deps);
        Path meta = root.resolve(".meta").resolve("check-" + deps);
        ReentrantLock lock = locks.computeIfAbsent(deps, key -> new ReentrantLock());
        lock.lock();
        try {
            Files.createDirectories(meta);
            NodeProjects.materialize(dir, files.entrySet().stream()
                    .map(e -> new StoredFile(e.getKey(), e.getValue())).toList());
            NodeProjects.installDependencies(dir, meta);
            return build(projectId, dir, meta);
        } catch (IllegalStateException e) {
            // no Node.js, npm install failed, ...: never block a generation because the check itself cannot run
            log.warn("Build check of project {} skipped: {}", projectId, e.getMessage());
            return BuildCheckResult.skipped(e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            lock.unlock();
        }
    }

    private BuildCheckResult build(Long projectId, Path dir, Path meta) throws IOException {
        Path buildLog = meta.resolve("build.log");
        Process build = NodeProjects.launch(dir, buildLog, "npx", "vite", "build",
                "--outDir", meta.resolve("dist").toString(), "--emptyOutDir", "--logLevel", "error");
        try {
            if (!build.waitFor(BUILD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                NodeProjects.kill(build);
                return BuildCheckResult.skipped("the test build took longer than " + BUILD_TIMEOUT_SECONDS + " s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return BuildCheckResult.skipped("interrupted");
        }
        if (build.exitValue() == 0) {
            log.info("Build check of project {} passed", projectId);
            return BuildCheckResult.passed();
        }
        String output = cleanOutput(Files.readString(buildLog, StandardCharsets.UTF_8), dir);
        log.info("Build check of project {} failed: {}", projectId, output.lines().findFirst().orElse(""));
        return BuildCheckResult.failed(output);
    }

    /** Removes colour codes, stack frames and the build folder's absolute path, so the model sees "src/App.tsx:3:1". */
    static String cleanOutput(String raw, Path dir) {
        String text = STACK_FRAME.matcher(ANSI.matcher(raw).replaceAll("")).replaceAll("");
        for (String prefix : List.of(dir.toString() + java.io.File.separator, dir.toString().replace('\\', '/') + "/")) {
            text = text.replace(prefix, "");
        }
        text = text.strip();
        return text.length() > MAX_OUTPUT_CHARS ? text.substring(0, MAX_OUTPUT_CHARS) + "\n..." : text;
    }
}
