package com.springproject.lovable_clone.controller;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.springproject.lovable_clone.dto.project.FileContentResponse;
import com.springproject.lovable_clone.dto.project.FileNode;
import com.springproject.lovable_clone.dto.project.FileTreeResponse;
import com.springproject.lovable_clone.security.AuthUtil;
import com.springproject.lovable_clone.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/files")
public class FileController {
    private final FileService fileService;
    private final AuthUtil authUtil;

    @GetMapping
    public ResponseEntity<List<FileNode>> getFileTree(@PathVariable Long projectId)
    {
        Long userId = authUtil.getCurrentUserId();
        return ResponseEntity.ok(fileService.getFileTree(projectId,userId));

    }
    @GetMapping("/{*path}") // /src/hooks/AppHook.jsx
    public ResponseEntity<FileContentResponse> getFile(
            @PathVariable long projectId,
            @PathVariable String path
    ){
        Long userId = authUtil.getCurrentUserId();
        return ResponseEntity.ok(fileService.getFileContent(projectId,path,userId));
    }
}
