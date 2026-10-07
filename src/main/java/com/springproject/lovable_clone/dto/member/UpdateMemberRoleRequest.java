package com.springproject.lovable_clone.dto.member;

import com.springproject.lovable_clone.enums.ProjectRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// have to make chages
public record UpdateMemberRoleRequest(@NotNull ProjectRole role) {

}
