package com.springproject.lovable_clone.entity;

import java.time.Instant;

public class ProjectFile {
    Long id;

    Project project;

    String path;
    String miniObjectKey;

    Instant createdAt;
    Instant updatedAt;

    User createdBy;
    User updatedBy;
}
