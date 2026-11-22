package com.springproject.lovable_clone.account.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Request and response bodies of the /api/billing endpoints.
 */
public final class BillingDtos {

    private BillingDtos() {}

    public record PlanResponse(
            Long id,
            String name,
            Integer priceCents,
            String price,
            Integer maxProjects,
            Integer maxTokensPerDay,
            Integer maxPreviews,
            Boolean unlimitedAi,
            boolean purchasable
    ) {}

    public record SubscriptionResponse(
            PlanResponse plan,
            String status,
            Instant currentPeriodEnd,
            boolean cancelAtPeriodEnd,
            boolean billingPortalAvailable
    ) {}

    public record CheckoutRequest(@NotNull Long planId) {}

    public record CheckoutResponse(String checkoutUrl, boolean mock) {}

    public record PortalResponse(String portalUrl) {}
}
