package com.springproject.lovable_clone.workspace.entity;

import com.springproject.lovable_clone.common.enums.PreviewStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "previews", indexes = @Index(name = "idx_previews_project_status", columnList = "project_id, status"))
public class Preview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "project_id", nullable = false)
    Long projectId;

    // who started it; previews count against this user's plan
    @Column(nullable = false)
    Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    PreviewStatus status;

    @Column(length = 1024)
    String previewUrl;

    // local mode: dev-server port; kubernetes mode: claimed runner pod
    Integer port;
    String podName;

    @Column(length = 2000)
    String failureReason;

    Instant startedAt;
    Instant expiresAt;
    Instant terminatedAt;

    @CreationTimestamp
    Instant createdAt;
}
