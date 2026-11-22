package com.springproject.lovable_clone.workspace.client;

import com.springproject.lovable_clone.common.dto.PlanDto;
import com.springproject.lovable_clone.common.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** account-service internal API; the caller's JWT is relayed automatically. */
@FeignClient(name = "account-service", url = "${ACCOUNT_SERVICE_URI:}", path = "/internal/v1")
public interface AccountClient {

    @GetMapping("/users/{id}")
    UserDto getUserById(@PathVariable("id") Long id);

    @GetMapping("/users")
    List<UserDto> getUsersByIds(@RequestParam("ids") List<Long> ids);

    @GetMapping("/users/by-email")
    UserDto getUserByEmail(@RequestParam("email") String email);

    @GetMapping("/billing/current-plan")
    PlanDto getCurrentPlan();
}
