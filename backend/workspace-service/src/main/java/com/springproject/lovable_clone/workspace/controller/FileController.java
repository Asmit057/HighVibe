package com.springproject.lovable_clone.workspace.controller;

import com.springproject.lovable_clone.common.dto.FileTreeDto;
import com.springproject.lovable_clone.common.enums.ProjectPermission;
import com.springproject.lovable_clone.common.security.AuthUtil;
import com.springproject.lovable_clone.workspace.dto.WorkspaceDtos.FileContentResponse;
import com.springproject.lovable_clone.workspace.dto.WorkspaceDtos.SaveFileRequest;
import com.springproject.lovable_clone.workspace.security.ProjectAccessService;
import com.springproject.lovable_clone.workspace.service.FilePaths;
import com.springproject.lovable_clone.workspace.service.ProjectFileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/files")
public class FileController {

    private final ProjectFileService projectFileService;
    private final ProjectAccessService access;
    private final AuthUtil authUtil;

    @GetMapping
    public ResponseEntity<FileTreeDto> getFileTree(@PathVariable Long projectId) {
        access.require(projectId, ProjectPermission.VIEW);
        return ResponseEntity.ok(projectFileService.getFileTree(projectId));
    }

    @GetMapping("/content")
    public ResponseEntity<FileContentResponse> getFile(@PathVariable Long projectId, @RequestParam String path) {
        access.require(projectId, ProjectPermission.VIEW);
        String normalized = FilePaths.normalize(path);
        return ResponseEntity.ok(new FileContentResponse(normalized, projectFileService.getFileContent(projectId, normalized)));
    }

    /** Manual edit from the in-browser code editor. */
    @PutMapping("/content")
    public ResponseEntity<FileContentResponse> saveFile(@PathVariable Long projectId, @RequestBody @Valid SaveFileRequest request) {
        Long userId = authUtil.getCurrentUserId();
        access.require(projectId, userId, ProjectPermission.EDIT);
        var file = projectFileService.saveFile(projectId, request.path(), request.content(), userId);
        return ResponseEntity.ok(new FileContentResponse(file.getPath(), request.content()));
    }

    @DeleteMapping("/content")
    public ResponseEntity<Void> deleteFile(@PathVariable Long projectId, @RequestParam String path) {
        access.require(projectId, ProjectPermission.EDIT);
        projectFileService.deleteFile(projectId, path);
        return ResponseEntity.noContent().build();
    }
}
