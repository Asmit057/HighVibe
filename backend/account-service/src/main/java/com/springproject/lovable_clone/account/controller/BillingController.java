package com.springproject.lovable_clone.account.controller;

import com.springproject.lovable_clone.account.dto.BillingDtos.*;
import com.springproject.lovable_clone.account.service.BillingService;
import com.springproject.lovable_clone.account.service.PlanService;
import com.springproject.lovable_clone.account.service.SubscriptionService;
import com.springproject.lovable_clone.common.security.AuthUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/billing")
public class BillingController {

    private final PlanService planService;
    private final SubscriptionService subscriptionService;
    private final BillingService billingService;
    private final AuthUtil authUtil;

    @GetMapping("/plans")
    public ResponseEntity<List<PlanResponse>> plans() {
        return ResponseEntity.ok(planService.getActivePlans());
    }

    @GetMapping("/subscription")
    public ResponseEntity<SubscriptionResponse> mySubscription() {
        return ResponseEntity.ok(subscriptionService.getCurrentSubscription(authUtil.getCurrentUserId()));
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(@RequestBody @Valid CheckoutRequest request) {
        return ResponseEntity.ok(billingService.createCheckout(authUtil.getCurrentUserId(), request.planId()));
    }

    @PostMapping("/portal")
    public ResponseEntity<PortalResponse> portal() {
        return ResponseEntity.ok(billingService.openCustomerPortal(authUtil.getCurrentUserId()));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
                                        @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        billingService.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
