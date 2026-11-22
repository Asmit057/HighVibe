package com.springproject.lovable_clone.workspace.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/** Metadata of one project file; the body lives in MinIO under {@link #minioObjectKey}. */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "project_files",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_files_project_path", columnNames = {"project_id", "path"}))
public class ProjectFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    Project project;

    @Column(nullable = false, length = 512)
    String path;

    @Column(nullable = false, length = 1024)
    String minioObjectKey;

    Long sizeBytes;

    Long createdBy;
    Long updatedBy;

    Instant createdAt;
    Instant updatedAt;
}
