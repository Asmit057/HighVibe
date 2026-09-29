package com.springproject.lovable_clone.mapper;

import com.springproject.lovable_clone.dto.auth.SignupRequest;
import com.springproject.lovable_clone.dto.auth.UserProfileResponse;
import com.springproject.lovable_clone.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User toEntity(SignupRequest signupRequest);

    UserProfileResponse toUserProfileResponse(User user);
}
