package com.springproject.lovable_clone.intelligence.client;

import com.springproject.lovable_clone.common.dto.BuildCheckRequest;
import com.springproject.lovable_clone.common.dto.BuildCheckResult;
import com.springproject.lovable_clone.common.dto.FileTreeDto;
import com.springproject.lovable_clone.common.enums.ProjectPermission;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** workspace-service internal API; the caller's JWT is relayed automatically. */
@FeignClient(name = "workspace-service", url = "${WORKSPACE_SERVICE_URI:}", path = "/internal/v1/projects")
public interface WorkspaceClient {

    @GetMapping("/{projectId}/files/tree")
    FileTreeDto getFileTree(@PathVariable("projectId") Long projectId);

    @GetMapping("/{projectId}/files/content")
    String getFileContent(@PathVariable("projectId") Long projectId, @RequestParam("path") String path);

    @GetMapping("/{projectId}/permissions/check")
    boolean checkPermission(@PathVariable("projectId") Long projectId, @RequestParam("permission") ProjectPermission permission);

    /** Test-builds the project with these files put over the saved ones (nothing is saved). */
    @PostMapping("/{projectId}/build-check")
    BuildCheckResult checkBuild(@PathVariable("projectId") Long projectId, @RequestBody BuildCheckRequest request);
}
