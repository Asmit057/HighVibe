package com.springproject.lovable_clone.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

import static com.springproject.lovable_clone.enums.ProjectPermission.*;

@RequiredArgsConstructor
@Getter
public enum ProjectRole {
    EDITOR(Set.of(VIEW,EDIT,DELETE)),
    VIEWER(Set.of(VIEW)),
    OWNER(Set.of(VIEW,EDIT,DELETE,MANAGE_MEMBERS));

    ProjectRole(ProjectPermission... permissions){
        this.permissions = Set.of(permissions);
    }
    private final Set<ProjectPermission> permissions;
}
