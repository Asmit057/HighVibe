package com.springproject.lovable_clone.account.controller;

import com.springproject.lovable_clone.account.service.SubscriptionService;
import com.springproject.lovable_clone.account.service.UserService;
import com.springproject.lovable_clone.common.dto.PlanDto;
import com.springproject.lovable_clone.common.dto.UserDto;
import com.springproject.lovable_clone.common.error.ResourceNotFoundException;
import com.springproject.lovable_clone.common.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Service-to-service API (called through Feign with the caller's JWT). Not routed by the gateway.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1")
public class InternalAccountController {

    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final AuthUtil authUtil;

    @GetMapping("/users/{id}")
    public UserDto getUserById(@PathVariable Long id) {
        return userService.getUserDto(id);
    }

    @GetMapping("/users")
    public List<UserDto> getUsersByIds(@RequestParam List<Long> ids) {
        return userService.findByIds(ids);
    }

    @GetMapping("/users/by-email")
    public UserDto getUserByEmail(@RequestParam String email) {
        return userService.findByEmail(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    /** Plan of the calling user (identified by the forwarded JWT). */
    @GetMapping("/billing/current-plan")
    public PlanDto getCurrentPlan() {
        return subscriptionService.getCurrentPlanDto(authUtil.getCurrentUserId());
    }
}
