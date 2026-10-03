package com.springproject.lovable_clone.security;

import com.springproject.lovable_clone.enums.ProjectPermission;
import com.springproject.lovable_clone.enums.ProjectRole;
import com.springproject.lovable_clone.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("security")
@RequiredArgsConstructor
public class SecurityExpressions {

    private final ProjectMemberRepository projectMemberRepository;
    private final AuthUtil authUtil;

    public boolean canViewProject(Long projectId)
    {
        Long userId = authUtil.getCurrentUserId();
        return projectMemberRepository.findRoleByProjectIdAndUserId(projectId,userId).
                map(role -> role.getPermissions().contains(ProjectPermission.VIEW))
                .orElse(false);

    }

    public boolean canEditProject(Long projectId)
    {
        Long userId = authUtil.getCurrentUserId();
        return projectMemberRepository.findRoleByProjectIdAndUserId(projectId,userId).
                map(role -> role.getPermissions().contains(ProjectPermission.EDIT))
                .orElse(false);
    }
}
