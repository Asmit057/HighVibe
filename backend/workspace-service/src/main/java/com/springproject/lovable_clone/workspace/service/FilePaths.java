package com.springproject.lovable_clone.workspace.service;

import com.springproject.lovable_clone.common.error.BadRequestException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalises and validates virtual project paths so a path can never escape its project
 * (no "..", no absolute paths, no backslashes, no control characters).
 */
public final class FilePaths {

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._@+\\-/ \\[\\]()]+");
    private static final int MAX_LENGTH = 512;

    private FilePaths() {}

    public static String normalize(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new BadRequestException("File path is required");
        }
        String path = rawPath.trim();
        while (path.startsWith("/") || path.startsWith("./")) {
            path = path.startsWith("/") ? path.substring(1) : path.substring(2);
        }
        if (path.isEmpty() || path.length() > MAX_LENGTH || path.contains("\\") || path.endsWith("/")
                || !ALLOWED.matcher(path).matches()) {
            throw new BadRequestException("Invalid file path: " + rawPath);
        }
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new BadRequestException("Invalid file path: " + rawPath);
            }
        }
        if (path.startsWith("node_modules/") || path.startsWith(".git/")) {
            throw new BadRequestException("Path is reserved: " + rawPath);
        }
        return path;
    }

    public static String contentType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js") || lower.endsWith(".mjs") || lower.endsWith(".jsx")
                || lower.endsWith(".ts") || lower.endsWith(".tsx")) return "text/javascript";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".md")) return "text/markdown";
        return "text/plain";
    }
}
