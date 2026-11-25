package com.springproject.lovable_clone.workspace.controller;

import com.springproject.lovable_clone.common.dto.BuildCheckRequest;
import com.springproject.lovable_clone.common.dto.BuildCheckResult;
import com.springproject.lovable_clone.common.dto.FileTreeDto;
import com.springproject.lovable_clone.common.enums.ProjectPermission;
import com.springproject.lovable_clone.workspace.security.ProjectAccessService;
import com.springproject.lovable_clone.workspace.service.ProjectFileService;
import com.springproject.lovable_clone.workspace.service.preview.BuildChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Service-to-service API used by intelligence-service with the user's relayed JWT.
 * Not routed by the gateway; every call is still checked against the caller's project role.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/projects/{projectId}")
public class InternalWorkspaceController {

    private final ProjectFileService projectFileService;
    private final ProjectAccessService access;
    private final BuildChecker buildChecker;

    @GetMapping("/files/tree")
    public FileTreeDto getFileTree(@PathVariable Long projectId) {
        access.require(projectId, ProjectPermission.VIEW);
        return projectFileService.getFileTree(projectId);
    }

    @GetMapping("/files/content")
    public String getFileContent(@PathVariable Long projectId, @RequestParam String path) {
        access.require(projectId, ProjectPermission.VIEW);
        return projectFileService.getFileContent(projectId, path);
    }

    @GetMapping("/permissions/check")
    public boolean checkPermission(@PathVariable Long projectId, @RequestParam ProjectPermission permission) {
        return access.hasPermission(projectId, permission);
    }

    /** Test-builds the project with the given files put over the saved ones; nothing is saved. */
    @PostMapping("/build-check")
    public BuildCheckResult checkBuild(@PathVariable Long projectId, @RequestBody BuildCheckRequest request) {
        access.require(projectId, ProjectPermission.EDIT);
        return buildChecker.check(projectId, request.files() == null ? Map.of() : request.files());
    }
}
