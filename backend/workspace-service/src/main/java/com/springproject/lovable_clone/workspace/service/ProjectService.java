package com.springproject.lovable_clone.workspace.service;

import com.springproject.lovable_clone.common.dto.PlanDto;
import com.springproject.lovable_clone.common.dto.UserDto;
import com.springproject.lovable_clone.common.enums.ProjectPermission;
import com.springproject.lovable_clone.common.enums.ProjectRole;
import com.springproject.lovable_clone.common.error.ResourceNotFoundException;
import com.springproject.lovable_clone.common.error.UsageLimitExceededException;
import com.springproject.lovable_clone.common.security.AuthUtil;
import com.springproject.lovable_clone.workspace.client.AccountClient;
import com.springproject.lovable_clone.workspace.dto.WorkspaceDtos.ProjectRequest;
import com.springproject.lovable_clone.workspace.dto.WorkspaceDtos.ProjectResponse;
import com.springproject.lovable_clone.workspace.dto.WorkspaceDtos.ProjectSummaryResponse;
import com.springproject.lovable_clone.workspace.entity.Project;
import com.springproject.lovable_clone.workspace.entity.ProjectMember;
import com.springproject.lovable_clone.workspace.entity.ProjectMemberId;
import com.springproject.lovable_clone.workspace.repository.ProjectMemberRepository;
import com.springproject.lovable_clone.workspace.repository.ProjectRepository;
import com.springproject.lovable_clone.workspace.security.ProjectAccessService;
import com.springproject.lovable_clone.workspace.service.preview.PreviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessService access;
    private final ProjectTemplateService templateService;
    private final PreviewService previewService;
    private final AccountClient accountClient;
    private final AuthUtil authUtil;

    @Transactional(readOnly = true)
    public List<ProjectSummaryResponse> getUserProjects() {
        Long userId = authUtil.getCurrentUserId();
        return projectRepository.findAllAccessibleByUser(userId).stream()
                .map(p -> new ProjectSummaryResponse(p.getId(), p.getName(), p.getDescription(),
                        projectMemberRepository.findAcceptedRole(p.getId(), userId).orElse(null),
                        p.getCreatedAt(), p.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(Long projectId) {
        ProjectRole role = access.require(projectId, ProjectPermission.VIEW);
        return toResponse(load(projectId), role);
    }

    @Transactional
    public ProjectResponse createProject(ProjectRequest request) {
        Long userId = authUtil.getCurrentUserId();

        PlanDto plan = accountClient.getCurrentPlan();
        long owned = projectRepository.countActiveProjectsWithRole(userId, ProjectRole.OWNER);
        if (plan.maxProjects() != null && owned >= plan.maxProjects()) {
            throw new UsageLimitExceededException("Your " + plan.name() + " plan allows " + plan.maxProjects()
                    + " projects. Upgrade to create more.");
        }

        Project project = projectRepository.save(Project.builder()
                .name(request.name().trim())
                .description(request.description())
                .isPublic(false)
                .build());

        Instant now = Instant.now();
        projectMemberRepository.save(ProjectMember.builder()
                .id(new ProjectMemberId(project.getId(), userId))
                .project(project)
                .projectRole(ProjectRole.OWNER)
                .invitedBy(userId)
                .invitedAt(now)
                .acceptedAt(now)
                .build());

        templateService.initializeProjectFromTemplate(project.getId(), userId);
        log.info("User {} created project {}", userId, project.getId());
        return toResponse(project, ProjectRole.OWNER);
    }

    @Transactional
    public ProjectResponse updateProject(Long projectId, ProjectRequest request) {
        ProjectRole role = access.require(projectId, ProjectPermission.EDIT);
        Project project = load(projectId);
        project.setName(request.name().trim());
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        return toResponse(projectRepository.save(project), role);
    }

    @Transactional
    public void softDelete(Long projectId) {
        access.require(projectId, ProjectPermission.DELETE);
        Project project = load(projectId);
        project.setDeletedAt(Instant.now());
        previewService.stopAllForProject(projectId);
        log.info("Project {} soft-deleted", projectId);
    }

    private Project load(Long projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));
    }

    private ProjectResponse toResponse(Project project, ProjectRole role) {
        UserDto owner = projectMemberRepository.findFirstByIdProjectIdAndProjectRole(project.getId(), ProjectRole.OWNER)
                .map(member -> safeUser(member.getId().getUserId()))
                .orElse(null);
        return new ProjectResponse(project.getId(), project.getName(), project.getDescription(),
                project.getCreatedAt(), project.getUpdatedAt(), role, owner);
    }

    private UserDto safeUser(Long userId) {
        try {
            return accountClient.getUserById(userId);
        } catch (RuntimeException e) {
            log.warn("Could not load user {} from account-service: {}", userId, e.getMessage());
            return new UserDto(userId, null, null);
        }
    }
}
