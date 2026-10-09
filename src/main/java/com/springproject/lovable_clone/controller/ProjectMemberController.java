package com.springproject.lovable_clone.controller;

import com.springproject.lovable_clone.dto.member.InviteMemberRequest;
import com.springproject.lovable_clone.dto.member.MemberResponse;
import com.springproject.lovable_clone.dto.member.UpdateMemberRoleRequest;
import com.springproject.lovable_clone.entity.ProjectMember;
import com.springproject.lovable_clone.security.AuthUtil;
import com.springproject.lovable_clone.service.ProjectMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {
    private final ProjectMemberService projectMemberService;
    AuthUtil authUtil;
    // get all the projects which the user is the member
    @GetMapping
    public ResponseEntity<List<MemberResponse>> getProjectMembers(@PathVariable Long projectId)
    {
        Long userId = authUtil.getCurrentUserId();
        return ResponseEntity.ok(projectMemberService.getProjectMembers(projectId,userId));
    }

    @PostMapping
    public ResponseEntity<MemberResponse> inviteMember(
        @PathVariable   Long projectId,
        @RequestBody @Valid InviteMemberRequest request
    ){
        Long userId = authUtil.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                projectMemberService.inviteMember(projectId, request,userId)
        );
    }

    @PatchMapping("/{memberId}")
    public ResponseEntity<MemberResponse> updateMemberRole(
            @PathVariable Long projectId,
            @PathVariable Long memberId,
            @RequestBody @Valid UpdateMemberRoleRequest request
    )
    {
        Long userId = authUtil.getCurrentUserId();
return ResponseEntity.ok(projectMemberService.updateMemberRole(projectId,memberId,request,userId));
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long projectId,
            @PathVariable Long memberId
    )
    {
        Long userId = authUtil.getCurrentUserId();
        projectMemberService.removeProjectMember(projectId,memberId,userId);
        return ResponseEntity.noContent().build();
    }
}
