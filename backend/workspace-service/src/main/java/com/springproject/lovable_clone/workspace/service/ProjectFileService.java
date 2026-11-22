package com.springproject.lovable_clone.workspace.service;

import com.springproject.lovable_clone.common.dto.FileNode;
import com.springproject.lovable_clone.common.dto.FileTreeDto;
import com.springproject.lovable_clone.common.error.ResourceNotFoundException;
import com.springproject.lovable_clone.workspace.entity.Project;
import com.springproject.lovable_clone.workspace.entity.ProjectFile;
import com.springproject.lovable_clone.workspace.repository.ProjectFileRepository;
import com.springproject.lovable_clone.workspace.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * File operations of a project. Callers are responsible for permission checks
 * (see ProjectAccessService); this class only stores and reads.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectFileService {

    public static final String FILE_TREE_CACHE = "fileTree";

    private final ProjectRepository projectRepository;
    private final ProjectFileRepository projectFileRepository;
    private final StorageService storageService;
    private final ApplicationEventPublisher events;

    @Cacheable(cacheNames = FILE_TREE_CACHE, key = "#projectId")
    @Transactional(readOnly = true)
    public FileTreeDto getFileTree(Long projectId) {
        List<FileNode> nodes = projectFileRepository.findByProjectIdOrderByPathAsc(projectId).stream()
                .map(f -> new FileNode(f.getPath(), f.getSizeBytes(), f.getUpdatedAt()))
                .toList();
        return new FileTreeDto(nodes);
    }

    @Transactional(readOnly = true)
    public String getFileContent(Long projectId, String rawPath) {
        String path = FilePaths.normalize(rawPath);
        ProjectFile file = projectFileRepository.findByProjectIdAndPath(projectId, path)
                .orElseThrow(() -> new ResourceNotFoundException("File", path));
        return new String(storageService.get(file.getMinioObjectKey()), StandardCharsets.UTF_8);
    }

    @CacheEvict(cacheNames = FILE_TREE_CACHE, key = "#projectId")
    @Transactional
    public ProjectFile saveFile(Long projectId, String rawPath, String content, Long userId) {
        String path = FilePaths.normalize(rawPath);
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String key = StorageService.objectKey(projectId, path);
        storageService.put(key, bytes, FilePaths.contentType(path));

        Instant now = Instant.now();
        ProjectFile file = projectFileRepository.findByProjectIdAndPath(projectId, path)
                .orElseGet(() -> ProjectFile.builder()
                        .project(project)
                        .path(path)
                        .createdAt(now)
                        .createdBy(userId)
                        .build());
        file.setMinioObjectKey(key);
        file.setSizeBytes((long) bytes.length);
        file.setUpdatedAt(now);
        file.setUpdatedBy(userId);
        file = projectFileRepository.save(file);

        project.setUpdatedAt(now);
        events.publishEvent(new FileChangedEvent(projectId, path, bytes));
        log.debug("Saved {} ({} bytes) in project {}", path, bytes.length, projectId);
        return file;
    }

    @CacheEvict(cacheNames = FILE_TREE_CACHE, key = "#projectId")
    @Transactional
    public void deleteFile(Long projectId, String rawPath) {
        String path = FilePaths.normalize(rawPath);
        ProjectFile file = projectFileRepository.findByProjectIdAndPath(projectId, path)
                .orElseThrow(() -> new ResourceNotFoundException("File", path));
        projectFileRepository.delete(file);
        storageService.delete(file.getMinioObjectKey());
        events.publishEvent(new FileChangedEvent(projectId, path, null));
    }

    /** All files of a project as a zip archive (for "Download code"). */
    @Transactional(readOnly = true)
    public byte[] zipProject(Long projectId) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (ProjectFile file : projectFileRepository.findByProjectIdOrderByPathAsc(projectId)) {
                zip.putNextEntry(new ZipEntry(file.getPath()));
                zip.write(storageService.get(file.getMinioObjectKey()));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }

    /** Raw bytes of every file, used to materialise a project on disk for previews. */
    @Transactional(readOnly = true)
    public List<StoredFile> loadAllFiles(Long projectId) {
        return projectFileRepository.findByProjectIdOrderByPathAsc(projectId).stream()
                .map(f -> new StoredFile(f.getPath(), storageService.get(f.getMinioObjectKey())))
                .toList();
    }

    public record StoredFile(String path, byte[] content) {}
}
