package com.springproject.lovable_clone.workspace.entity;

import com.springproject.lovable_clone.common.enums.ProjectRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * Membership of a user (owned by account-service, referenced by id) in a project.
 */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "project_members", indexes = @Index(name = "idx_project_members_user", columnList = "user_id"))
public class ProjectMember {

    @EmbeddedId
    ProjectMemberId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("projectId")
    Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    ProjectRole projectRole;

    Long invitedBy;
    Instant invitedAt;
    // null until the invitee accepts; pending members cannot access the project
    Instant acceptedAt;

    public boolean isAccepted() {
        return acceptedAt != null;
    }
}
