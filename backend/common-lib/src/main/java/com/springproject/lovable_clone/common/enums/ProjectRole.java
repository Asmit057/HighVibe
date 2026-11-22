package com.springproject.lovable_clone.common.enums;

import lombok.Getter;

import java.util.Set;

import static com.springproject.lovable_clone.common.enums.ProjectPermission.*;

@Getter
public enum ProjectRole {
    OWNER(VIEW, EDIT, DELETE, MANAGE_MEMBERS),
    EDITOR(VIEW, EDIT),
    VIEWER(VIEW);

    private final Set<ProjectPermission> permissions;

    ProjectRole(ProjectPermission... permissions) {
        this.permissions = Set.of(permissions);
    }

    public boolean can(ProjectPermission permission) {
        return permissions.contains(permission);
    }
}
