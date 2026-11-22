package com.springproject.lovable_clone.workspace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Seeds new projects with the React + TypeScript + Vite + Tailwind CSS 4 + daisyUI 5 starter
 * that the AI builds on (packaged under resources/templates/react-vite-starter).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectTemplateService {

    private static final String TEMPLATE_DIR = "templates/react-vite-starter/";

    private final ProjectFileService projectFileService;
    private volatile Map<String, String> templateFiles;

    public void initializeProjectFromTemplate(Long projectId, Long userId) {
        templateFiles().forEach((path, content) -> projectFileService.saveFile(projectId, path, content, userId));
        log.info("Project {} initialised from template ({} files)", projectId, templateFiles().size());
    }

    Map<String, String> templateFiles() {
        Map<String, String> files = templateFiles;
        if (files == null) {
            files = loadTemplate();
            templateFiles = files;
        }
        return files;
    }

    private static Map<String, String> loadTemplate() {
        try {
            Map<String, String> files = new LinkedHashMap<>();
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:" + TEMPLATE_DIR + "**/*");
            for (Resource resource : resources) {
                if (!resource.isReadable() || resource.getFilename() == null || resource.getFilename().isEmpty()) {
                    continue;
                }
                String url = resource.getURL().toString().replace('\\', '/');
                int index = url.lastIndexOf(TEMPLATE_DIR);
                if (index < 0 || url.endsWith("/")) {
                    continue;
                }
                String relative = url.substring(index + TEMPLATE_DIR.length());
                files.put(relative, resource.getContentAsString(StandardCharsets.UTF_8));
            }
            if (files.isEmpty()) {
                throw new IllegalStateException("Project template is missing from the classpath");
            }
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load the project template", e);
        }
    }
}
