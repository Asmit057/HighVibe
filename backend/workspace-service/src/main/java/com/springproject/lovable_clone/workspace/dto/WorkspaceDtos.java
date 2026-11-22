package com.springproject.lovable_clone.workspace.dto;

import com.springproject.lovable_clone.common.dto.UserDto;
import com.springproject.lovable_clone.common.enums.PreviewStatus;
import com.springproject.lovable_clone.common.enums.ProjectRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request and response bodies of workspace-service.
 */
public final class WorkspaceDtos {

    private WorkspaceDtos() {}

    // ---------------------------------------------------------------- projects

    public record ProjectRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description
    ) {}

    public record ProjectResponse(
            Long id,
            String name,
            String description,
            Instant createdAt,
            Instant updatedAt,
            ProjectRole role,
            UserDto owner
    ) {}

    public record ProjectSummaryResponse(
            Long id,
            String name,
            String description,
            ProjectRole role,
            Instant createdAt,
            Instant updatedAt
    ) {}

    // ---------------------------------------------------------------- members

    public record InviteMemberRequest(
            @Email @NotBlank String username,
            @NotNull ProjectRole role
    ) {}

    public record UpdateMemberRoleRequest(@NotNull ProjectRole role) {}

    public record MemberResponse(
            Long userId,
            String username,
            String name,
            ProjectRole projectRole,
            Instant invitedAt,
            Instant acceptedAt
    ) {}

    public record InvitationResponse(
            Long projectId,
            String projectName,
            ProjectRole role,
            Instant invitedAt
    ) {}

    // ---------------------------------------------------------------- files

    public record FileContentResponse(String path, String content) {}

    public record SaveFileRequest(
            @NotBlank @Size(max = 512) String path,
            @NotNull @Size(max = 1_000_000) String content
    ) {}

    // ---------------------------------------------------------------- previews

    public record PreviewResponse(
            Long id,
            Long projectId,
            PreviewStatus status,
            String previewUrl,
            String failureReason,
            Instant startedAt,
            Instant expiresAt
    ) {}
}
