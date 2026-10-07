package com.springproject.lovable_clone.dto.member;

import com.springproject.lovable_clone.enums.ProjectRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

//  in pom.xml we are adding the spring starter validation
public record InviteMemberRequest(
       @Email @NotBlank  String username,
       @NotNull ProjectRole role
) {
}
