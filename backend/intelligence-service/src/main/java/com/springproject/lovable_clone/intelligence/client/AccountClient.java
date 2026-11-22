package com.springproject.lovable_clone.intelligence.client;

import com.springproject.lovable_clone.common.dto.PlanDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/** account-service internal API; the caller's JWT is relayed automatically. */
@FeignClient(name = "account-service", url = "${ACCOUNT_SERVICE_URI:}", path = "/internal/v1")
public interface AccountClient {

    @GetMapping("/billing/current-plan")
    PlanDto getCurrentPlan();
}
