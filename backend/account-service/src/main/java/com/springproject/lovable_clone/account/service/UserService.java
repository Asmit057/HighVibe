package com.springproject.lovable_clone.account.service;

import com.springproject.lovable_clone.account.dto.AuthDtos.UserProfileResponse;
import com.springproject.lovable_clone.account.entity.User;
import com.springproject.lovable_clone.account.repository.UserRepository;
import com.springproject.lovable_clone.common.dto.UserDto;
import com.springproject.lovable_clone.common.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));
    }

    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
    }

    public UserProfileResponse getProfile(Long userId) {
        return toProfile(getUser(userId));
    }

    public UserDto getUserDto(Long userId) {
        return toDto(getUser(userId));
    }

    public Optional<UserDto> findByEmail(String email) {
        return userRepository.findByUsernameIgnoreCase(email).map(UserService::toDto);
    }

    public List<UserDto> findByIds(Collection<Long> ids) {
        return userRepository.findAllByIdIn(ids).stream().map(UserService::toDto).toList();
    }

    public static UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getName());
    }

    public static UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getName());
    }
}
