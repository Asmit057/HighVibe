package com.springproject.lovable_clone.workspace.controller;

import com.springproject.lovable_clone.workspace.dto.WorkspaceDtos.*;
import com.springproject.lovable_clone.workspace.service.ProjectMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService memberService;

    @GetMapping("/api/projects/{projectId}/members")
    public ResponseEntity<List<MemberResponse>> members(@PathVariable Long projectId) {
        return ResponseEntity.ok(memberService.getProjectMembers(projectId));
    }

    @PostMapping("/api/projects/{projectId}/members")
    public ResponseEntity<MemberResponse> invite(@PathVariable Long projectId,
                                                 @RequestBody @Valid InviteMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memberService.inviteMember(projectId, request));
    }

    @PatchMapping("/api/projects/{projectId}/members/{memberId}")
    public ResponseEntity<MemberResponse> updateRole(@PathVariable Long projectId, @PathVariable Long memberId,
                                                     @RequestBody @Valid UpdateMemberRoleRequest request) {
        return ResponseEntity.ok(memberService.updateMemberRole(projectId, memberId, request));
    }

    @DeleteMapping("/api/projects/{projectId}/members/{memberId}")
    public ResponseEntity<Void> remove(@PathVariable Long projectId, @PathVariable Long memberId) {
        memberService.removeProjectMember(projectId, memberId);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- invitations of the caller

    @GetMapping("/api/invitations")
    public ResponseEntity<List<InvitationResponse>> myInvitations() {
        return ResponseEntity.ok(memberService.getMyInvitations());
    }

    @PostMapping("/api/invitations/{projectId}/accept")
    public ResponseEntity<Void> accept(@PathVariable Long projectId) {
        memberService.acceptInvitation(projectId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/invitations/{projectId}")
    public ResponseEntity<Void> decline(@PathVariable Long projectId) {
        memberService.declineInvitation(projectId);
        return ResponseEntity.noContent().build();
    }
}
