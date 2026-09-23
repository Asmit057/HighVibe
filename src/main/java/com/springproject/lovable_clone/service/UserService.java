package com.springproject.lovable_clone.service;

import com.springproject.lovable_clone.dto.auth.UserProfileResponse;

public interface UserService {
     UserProfileResponse getProfile(Long userId);
}
