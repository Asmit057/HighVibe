package com.springproject.lovable_clone.service.impl;

import com.springproject.lovable_clone.dto.project.FileContentResponse;
import com.springproject.lovable_clone.dto.project.FileNode;
import com.springproject.lovable_clone.service.FileService;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FileServiceImpl implements FileService {
    @Override
    public @Nullable List<FileNode> getFileTree(Long projectId, Long userId) {
        return List.of();
    }

    @Override
    public @Nullable FileContentResponse getFileContent(long projectId, String path, Long userId) {
        return null;
    }
}
