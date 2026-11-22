package com.springproject.lovable_clone.workspace.security;

import com.springproject.lovable_clone.common.enums.ProjectPermission;
import com.springproject.lovable_clone.common.enums.ProjectRole;
import com.springproject.lovable_clone.common.error.ResourceNotFoundException;
import com.springproject.lovable_clone.common.security.AuthUtil;
import com.springproject.lovable_clone.workspace.repository.ProjectMemberRepository;
import com.springproject.lovable_clone.workspace.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Role-based project authorization used by every workspace operation.
 *
 * A caller who is not an accepted member gets 404 (the project's existence is not revealed);
 * a member lacking the permission gets 403.
 */
@Component
@RequiredArgsConstructor
public class ProjectAccessService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final AuthUtil authUtil;

    public Optional<ProjectRole> roleOf(Long projectId, Long userId) {
        if (projectRepository.findByIdAndDeletedAtIsNull(projectId).isEmpty()) {
            return Optional.empty();
        }
        return projectMemberRepository.findAcceptedRole(projectId, userId);
    }

    public boolean hasPermission(Long projectId, ProjectPermission permission) {
        return roleOf(projectId, authUtil.getCurrentUserId()).map(role -> role.can(permission)).orElse(false);
    }

    public ProjectRole require(Long projectId, Long userId, ProjectPermission permission) {
        ProjectRole role = roleOf(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", String.valueOf(projectId)));
        if (!role.can(permission)) {
            throw new AccessDeniedException("Missing permission " + permission.getValue());
        }
        return role;
    }

    public ProjectRole require(Long projectId, ProjectPermission permission) {
        return require(projectId, authUtil.getCurrentUserId(), permission);
    }
}
