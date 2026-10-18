package com.springproject.lovable_clone.controller;

import com.springproject.lovable_clone.dto.subscription.*;
import com.springproject.lovable_clone.security.AuthUtil;
import com.springproject.lovable_clone.service.PlanService;
import com.springproject.lovable_clone.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor

public class BillingController {
    private final PlanService planService;
    private final SubscriptionService subscriptionService;
    private final AuthUtil authUtil;

    @GetMapping("/api/plans")
    public ResponseEntity<List<PlanResponse>> getAllPlans(){
        return ResponseEntity.ok(planService.getAllActivePlans());
    }

    @GetMapping("/api/me/subscription")
    public ResponseEntity<SubscriptionResponse> getMySubscription()
    {
        Long userId = authUtil.getCurrentUserId();
        return ResponseEntity.ok(subscriptionService.getCurrentSubscription(userId));

    }

    @PostMapping("/api/stripe/checkout")
    public  ResponseEntity<CheckoutResponse> createCheckoutResponse(
            @RequestBody CheckoutRequest request
    ){
     Long userId = authUtil.getCurrentUserId();
     return ResponseEntity.ok(subscriptionService.createCheckoutSessionUrl(request,userId));
    }

    @PostMapping("/api/stripe/portal")
    public ResponseEntity<PortalResponse> openCustomerPortal(){
        Long userId = authUtil.getCurrentUserId();
        return  ResponseEntity.ok(subscriptionService.openCustomerPortal(userId));
    }

}
