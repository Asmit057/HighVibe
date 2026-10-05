package com.springproject.lovable_clone.service.impl;

import com.springproject.lovable_clone.dto.project.ProjectRequest;
import com.springproject.lovable_clone.dto.project.ProjectResponse;
import com.springproject.lovable_clone.dto.project.ProjectSummaryResponse;
import com.springproject.lovable_clone.entity.Project;
import com.springproject.lovable_clone.entity.ProjectMember;
import com.springproject.lovable_clone.entity.ProjectMemberId;
import com.springproject.lovable_clone.entity.User;
import com.springproject.lovable_clone.enums.ProjectRole;
import com.springproject.lovable_clone.error.ResourceNotFoundException;
import com.springproject.lovable_clone.mapper.ProjectMapper;
import com.springproject.lovable_clone.repository.ProjectMemberRepository;
import com.springproject.lovable_clone.repository.ProjectRepository;
import com.springproject.lovable_clone.repository.UserRepository;
import com.springproject.lovable_clone.security.AuthUtil;
import com.springproject.lovable_clone.service.ProjectService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Transactional
public class ProjectServiceImpl implements ProjectService {

    ProjectRepository projectRepository;
    UserRepository userRepository;
    ProjectMapper projectMapper;
    ProjectMemberRepository projectMemberRepository;
     AuthUtil authUtil;
    @Override
    public ProjectResponse createProject(ProjectRequest request) {
        Long userId = authUtil.getCurrentUserId();
//        User owner = userRepository.findById(userId).orElseThrow(
//                () -> new ResourceNotFoundException("User", userId.toString())
//        );
        User owner = userRepository.getReferenceById(userId);
        // this is builder pattern
        // first object initialize them , then it will take the previous object
        // then it will initialize the value
        Project project = Project.builder().name(request.name()).isPublic(false).build();
        project = projectRepository.save(project);
        ProjectMemberId projectMemberId = new ProjectMemberId(project.getId(), owner.getId() );
        ProjectMember projectMember = ProjectMember.builder()
                .id(projectMemberId)
                .projectRole(ProjectRole.OWNER)
                .user(owner)
                .acceptedAt(Instant.now())
                .project(project)
                .build();

        projectMemberRepository.save(projectMember);
        return projectMapper.toProjectResponse(project);
    }

    @Override
    public List<ProjectSummaryResponse> getUserProjects() {
//        return projectRepository.findAllAccessibleByUser(userId)
//                .stream()
//                .map(project -> projectMapper.toProjectSummaryResponse(project))
//                .collect(Collectors.toList());
        Long userId = authUtil.getCurrentUserId();
        var projects = projectRepository.findAllAccessibleByUser(userId);
        return projectMapper.toListOfProjectSummaryResponse(projects);
    }


    // to get the project by id only
    // and check whether user have access
    @Override
    @PreAuthorize("@security.canViewProject(#projectId)")
    public ProjectResponse getUserProjectById(Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        Project project = getAccessibleProjectById(projectId,userId);
        return  projectMapper.toProjectResponse(project);
    }



    @Override
    @PreAuthorize("@security.canEditProject(#projectId)")
    public ProjectResponse updateProject(Long id, ProjectRequest request) {
        Long userId = authUtil.getCurrentUserId();
        Project project = projectRepository.findAccessibleProjectById(id, userId).orElseThrow();


        project.setName(request.name());
        project  = projectRepository.save(project);
        return projectMapper.toProjectResponse(project);
    }

    @Override
    public void softDelete(Long id) {
        Long userId = authUtil.getCurrentUserId();
       Project project = getAccessibleProjectById(id, userId);


     project.setDeletedAt(Instant.now());
       projectRepository.save(project);
    }

    ///  internal functions
    public Project getAccessibleProjectById(Long projectId, Long userId)
    {
        return projectRepository.findAccessibleProjectById(projectId, userId).orElseThrow(() ->
        new ResourceNotFoundException("Project",projectId.toString()));
    }
}
