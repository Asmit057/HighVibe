package com.springproject.lovable_clone.workspace.service;

import com.springproject.lovable_clone.common.dto.UserDto;
import com.springproject.lovable_clone.common.enums.ProjectPermission;
import com.springproject.lovable_clone.common.enums.ProjectRole;
import com.springproject.lovable_clone.common.error.BadRequestException;
import com.springproject.lovable_clone.common.error.ResourceNotFoundException;
import com.springproject.lovable_clone.common.security.AuthUtil;
import com.springproject.lovable_clone.workspace.client.AccountClient;
import com.springproject.lovable_clone.workspace.dto.WorkspaceDtos.*;
import com.springproject.lovable_clone.workspace.entity.Project;
import com.springproject.lovable_clone.workspace.entity.ProjectMember;
import com.springproject.lovable_clone.workspace.entity.ProjectMemberId;
import com.springproject.lovable_clone.workspace.repository.ProjectMemberRepository;
import com.springproject.lovable_clone.workspace.repository.ProjectRepository;
import com.springproject.lovable_clone.workspace.security.ProjectAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Team management. Only owners (MANAGE_MEMBERS) invite, re-role or remove; any member may leave.
 * Invitations stay pending until the invitee accepts them.
 */
@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService access;
    private final AccountClient accountClient;
    private final AuthUtil authUtil;

    @Transactional(readOnly = true)
    public List<MemberResponse> getProjectMembers(Long projectId) {
        access.require(projectId, ProjectPermission.VIEW);
        List<ProjectMember> members = memberRepository.findByIdProjectIdOrderByInvitedAtAsc(projectId);
        List<Long> ids = members.stream().map(m -> m.getId().getUserId()).toList();
        Map<Long, UserDto> users = ids.isEmpty() ? Map.of()
                : accountClient.getUsersByIds(ids).stream().collect(Collectors.toMap(UserDto::id, Function.identity()));
        return members.stream().map(m -> toResponse(m, users.get(m.getId().getUserId()))).toList();
    }

    @Transactional
    public MemberResponse inviteMember(Long projectId, InviteMemberRequest request) {
        Long callerId = authUtil.getCurrentUserId();
        access.require(projectId, callerId, ProjectPermission.MANAGE_MEMBERS);
        if (request.role() == ProjectRole.OWNER) {
            throw new BadRequestException("A project has exactly one owner; invite as EDITOR or VIEWER");
        }

        UserDto invitee = accountClient.getUserByEmail(request.username().trim());
        if (invitee.id().equals(callerId)) {
            throw new BadRequestException("You cannot invite yourself");
        }
        ProjectMemberId id = new ProjectMemberId(projectId, invitee.id());
        if (memberRepository.existsById(id)) {
            throw new BadRequestException(invitee.username() + " is already a member or has a pending invitation");
        }

        Project project = projectRepository.getReferenceById(projectId);
        ProjectMember member = memberRepository.save(ProjectMember.builder()
                .id(id)
                .project(project)
                .projectRole(request.role())
                .invitedBy(callerId)
                .invitedAt(Instant.now())
                .build());
        return toResponse(member, invitee);
    }

    @Transactional
    public MemberResponse updateMemberRole(Long projectId, Long memberId, UpdateMemberRoleRequest request) {
        access.require(projectId, ProjectPermission.MANAGE_MEMBERS);
        if (request.role() == ProjectRole.OWNER) {
            throw new BadRequestException("Ownership cannot be assigned through a role change");
        }
        ProjectMember member = find(projectId, memberId);
        if (member.getProjectRole() == ProjectRole.OWNER) {
            throw new BadRequestException("The owner role cannot be changed");
        }
        member.setProjectRole(request.role());
        return toResponse(member, accountClient.getUserById(memberId));
    }

    @Transactional
    public void removeProjectMember(Long projectId, Long memberId) {
        Long callerId = authUtil.getCurrentUserId();
        ProjectMember member = find(projectId, memberId);
        if (member.getProjectRole() == ProjectRole.OWNER) {
            throw new BadRequestException("The owner cannot be removed from the project");
        }
        if (!memberId.equals(callerId)) {
            access.require(projectId, callerId, ProjectPermission.MANAGE_MEMBERS);
        }
        memberRepository.delete(member);
    }

    // ---------------------------------------------------------------- invitations

    @Transactional(readOnly = true)
    public List<InvitationResponse> getMyInvitations() {
        return memberRepository.findPendingInvitations(authUtil.getCurrentUserId()).stream()
                .map(m -> new InvitationResponse(m.getProject().getId(), m.getProject().getName(),
                        m.getProjectRole(), m.getInvitedAt()))
                .toList();
    }

    @Transactional
    public void acceptInvitation(Long projectId) {
        ProjectMember member = pendingInvitation(projectId);
        member.setAcceptedAt(Instant.now());
    }

    @Transactional
    public void declineInvitation(Long projectId) {
        memberRepository.delete(pendingInvitation(projectId));
    }

    private ProjectMember pendingInvitation(Long projectId) {
        ProjectMember member = memberRepository.findById(new ProjectMemberId(projectId, authUtil.getCurrentUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", projectId.toString()));
        if (member.isAccepted()) {
            throw new BadRequestException("Invitation already accepted");
        }
        return member;
    }

    private ProjectMember find(Long projectId, Long memberId) {
        access.require(projectId, ProjectPermission.VIEW);
        return memberRepository.findById(new ProjectMemberId(projectId, memberId))
                .orElseThrow(() -> new ResourceNotFoundException("Member", memberId.toString()));
    }

    private static MemberResponse toResponse(ProjectMember member, UserDto user) {
        return new MemberResponse(
                member.getId().getUserId(),
                user == null ? null : user.username(),
                user == null ? null : user.name(),
                member.getProjectRole(),
                member.getInvitedAt(),
                member.getAcceptedAt());
    }
}
