package com.springproject.lovable_clone.dto.project;

import com.springproject.lovable_clone.dto.auth.UserProfileResponse;

import java.time.Instant;

public record
ProjectResponse (
        Long id,
        String name,
        Instant createdAt,
        UserProfileResponse owner) {

}
