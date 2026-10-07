package com.springproject.lovable_clone.dto.member;

import com.springproject.lovable_clone.enums.ProjectRole;

import java.time.Instant;

public record MemberResponse (
        Long userId,
        String username,
        String name,
        ProjectRole projectRole,
        Instant invitedAt
){
}
