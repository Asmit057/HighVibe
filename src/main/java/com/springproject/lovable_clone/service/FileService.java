package com.springproject.lovable_clone.service;

import com.springproject.lovable_clone.dto.project.FileContentResponse;
import com.springproject.lovable_clone.dto.project.FileNode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


public interface FileService {
    @Nullable List<FileNode> getFileTree(Long projectId, Long userId);

    @Nullable FileContentResponse getFileContent(long projectId, String path, Long userId);
}
