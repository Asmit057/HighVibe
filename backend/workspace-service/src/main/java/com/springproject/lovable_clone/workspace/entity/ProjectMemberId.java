package com.springproject.lovable_clone.workspace.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

/** Composite key of project_members: one role per (project, user). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProjectMemberId implements Serializable {
    Long projectId;
    Long userId;
}
