package com.springproject.lovable_clone.workspace.controller;

import com.springproject.lovable_clone.common.enums.ProjectPermission;
import com.springproject.lovable_clone.workspace.dto.WorkspaceDtos.*;
import com.springproject.lovable_clone.workspace.security.ProjectAccessService;
import com.springproject.lovable_clone.workspace.service.ProjectFileService;
import com.springproject.lovable_clone.workspace.service.ProjectService;
import com.springproject.lovable_clone.workspace.service.preview.PreviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final PreviewService previewService;
    private final ProjectFileService projectFileService;
    private final ProjectAccessService access;

    @GetMapping
    public ResponseEntity<List<ProjectSummaryResponse>> myProjects() {
        return ResponseEntity.ok(projectService.getUserProjects());
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.getProject(projectId));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@RequestBody @Valid ProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.createProject(request));
    }

    @PatchMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> updateProject(@PathVariable Long projectId,
                                                         @RequestBody @Valid ProjectRequest request) {
        return ResponseEntity.ok(projectService.updateProject(projectId, request));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long projectId) {
        projectService.softDelete(projectId);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- live preview

    @PostMapping("/{projectId}/deploy")
    public ResponseEntity<PreviewResponse> deploy(@PathVariable Long projectId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(previewService.deploy(projectId));
    }

    @GetMapping("/{projectId}/preview")
    public ResponseEntity<PreviewResponse> preview(@PathVariable Long projectId) {
        return ResponseEntity.ok(previewService.getLatest(projectId));
    }

    @DeleteMapping("/{projectId}/preview")
    public ResponseEntity<Void> stopPreview(@PathVariable Long projectId) {
        previewService.stop(projectId);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- export

    @GetMapping("/{projectId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long projectId) {
        access.require(projectId, ProjectPermission.VIEW);
        byte[] zip = projectFileService.zipProject(projectId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("project-" + projectId + ".zip").build().toString())
                .body(zip);
    }
}
