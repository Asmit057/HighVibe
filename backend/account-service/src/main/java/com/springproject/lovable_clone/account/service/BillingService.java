package com.springproject.lovable_clone.account.service;

import com.springproject.lovable_clone.account.config.AccountProperties;
import com.springproject.lovable_clone.account.dto.BillingDtos.CheckoutResponse;
import com.springproject.lovable_clone.account.dto.BillingDtos.PortalResponse;
import com.springproject.lovable_clone.account.entity.Plan;
import com.springproject.lovable_clone.account.entity.ProcessedWebhookEvent;
import com.springproject.lovable_clone.account.entity.User;
import com.springproject.lovable_clone.account.repository.PlanRepository;
import com.springproject.lovable_clone.account.repository.ProcessedWebhookEventRepository;
import com.springproject.lovable_clone.account.repository.UserRepository;
import com.springproject.lovable_clone.common.enums.SubscriptionStatus;
import com.springproject.lovable_clone.common.error.BadRequestException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Stripe checkout, customer portal and webhooks. Without a Stripe key (local development)
 * and with mock mode on, checkout activates the plan immediately instead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final AccountProperties properties;
    private final PlanService planService;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;

    @Transactional
    public CheckoutResponse createCheckout(Long userId, Long planId) {
        Plan plan = planService.getPlan(planId);
        if (!Boolean.TRUE.equals(plan.getActive())) {
            throw new BadRequestException("Plan " + plan.getName() + " is not available");
        }
        String billingPage = properties.frontendUrl() + "/billing";

        if (plan.isFree()) {
            subscriptionService.cancelLiveSubscription(userId);
            return new CheckoutResponse(billingPage + "?checkout=success", true);
        }

        if (!properties.billing().stripeEnabled()) {
            if (!properties.billing().mockMode()) {
                throw new BadRequestException("Billing is not configured on this server");
            }
            log.warn("Stripe is not configured: activating plan {} for user {} without payment (mock mode)",
                    plan.getName(), userId);
            subscriptionService.activateSubscription(userId, planId, null, null, null, null);
            return new CheckoutResponse(billingPage + "?checkout=success", true);
        }

        if (plan.getStripePriceId() == null || plan.getStripePriceId().isBlank()) {
            throw new BadRequestException("Plan " + plan.getName() + " has no Stripe price configured");
        }

        User user = userRepository.findById(userId).orElseThrow();
        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(plan.getStripePriceId())
                        .setQuantity(1L)
                        .build())
                .setClientReferenceId(userId.toString())
                .setSuccessUrl(billingPage + "?checkout=success&session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(billingPage + "?checkout=cancel")
                .putMetadata("user_id", userId.toString())
                .putMetadata("plan_id", plan.getId().toString());
        if (user.getStripeCustomerId() == null) {
            params.setCustomerEmail(user.getUsername());
        } else {
            params.setCustomer(user.getStripeCustomerId());
        }

        try {
            Session session = Session.create(params.build());
            return new CheckoutResponse(session.getUrl(), false);
        } catch (StripeException e) {
            log.error("Stripe checkout failed for user {}", userId, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider error, please retry");
        }
    }

    @Transactional(readOnly = true)
    public PortalResponse openCustomerPortal(Long userId) {
        String returnUrl = properties.frontendUrl() + "/billing";
        if (!properties.billing().stripeEnabled()) {
            return new PortalResponse(returnUrl);
        }
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getStripeCustomerId() == null) {
            throw new BadRequestException("No billing account yet: subscribe to a paid plan first");
        }
        try {
            var session = com.stripe.model.billingportal.Session.create(
                    com.stripe.param.billingportal.SessionCreateParams.builder()
                            .setCustomer(user.getStripeCustomerId())
                            .setReturnUrl(returnUrl)
                            .build());
            return new PortalResponse(session.getUrl());
        } catch (StripeException e) {
            log.error("Stripe portal failed for user {}", userId, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider error, please retry");
        }
    }

    /** Verifies the Stripe signature, then applies each event exactly once. */
    @Transactional
    public void handleWebhook(String payload, String signature) {
        String secret = properties.billing().stripeWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new BadRequestException("Stripe webhooks are not configured");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, secret);
        } catch (SignatureVerificationException e) {
            throw new BadRequestException("Invalid Stripe signature");
        }

        if (processedWebhookEventRepository.existsById(event.getId())) {
            log.info("Stripe event {} already processed", event.getId());
            return;
        }

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject object = deserializer.getObject().orElse(null);
        if (object == null) {
            try {
                object = deserializer.deserializeUnsafe();
            } catch (Exception e) {
                log.error("Could not deserialize Stripe event {}", event.getType(), e);
                return;
            }
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> onCheckoutCompleted((Session) object);
            case "customer.subscription.updated", "customer.subscription.created" ->
                    onSubscriptionUpdated((com.stripe.model.Subscription) object);
            case "customer.subscription.deleted" ->
                    subscriptionService.cancelSubscription(((com.stripe.model.Subscription) object).getId());
            case "invoice.paid" -> onInvoicePaid((Invoice) object);
            case "invoice.payment_failed" -> {
                String subscriptionId = subscriptionIdOf((Invoice) object);
                if (subscriptionId != null) subscriptionService.markSubscriptionPastDue(subscriptionId);
            }
            default -> log.debug("Ignoring Stripe event {}", event.getType());
        }

        processedWebhookEventRepository.save(new ProcessedWebhookEvent(event.getId(), event.getType(), Instant.now()));
    }

    private void onCheckoutCompleted(Session session) {
        Map<String, String> metadata = session.getMetadata();
        if (metadata == null || metadata.get("user_id") == null || metadata.get("plan_id") == null) {
            log.warn("Checkout session {} has no HighVibe metadata", session.getId());
            return;
        }
        Long userId = Long.parseLong(metadata.get("user_id"));
        Long planId = Long.parseLong(metadata.get("plan_id"));

        User user = userRepository.findById(userId).orElseThrow();
        if (user.getStripeCustomerId() == null && session.getCustomer() != null) {
            user.setStripeCustomerId(session.getCustomer());
        }

        Instant start = null;
        Instant end = null;
        try {
            var subscription = com.stripe.model.Subscription.retrieve(session.getSubscription());
            SubscriptionItem item = subscription.getItems().getData().getFirst();
            start = toInstant(item.getCurrentPeriodStart());
            end = toInstant(item.getCurrentPeriodEnd());
        } catch (StripeException e) {
            log.warn("Could not load Stripe subscription {}, using default period", session.getSubscription());
        }
        subscriptionService.activateSubscription(userId, planId, session.getSubscription(), session.getCustomer(), start, end);
    }

    private void onSubscriptionUpdated(com.stripe.model.Subscription subscription) {
        SubscriptionStatus status = mapStatus(subscription.getStatus());
        if (status == null) {
            return;
        }
        SubscriptionItem item = subscription.getItems().getData().getFirst();
        Long planId = item.getPrice() == null ? null
                : planRepository.findByStripePriceId(item.getPrice().getId()).map(Plan::getId).orElse(null);
        subscriptionService.updateSubscription(subscription.getId(), status,
                toInstant(item.getCurrentPeriodStart()), toInstant(item.getCurrentPeriodEnd()),
                subscription.getCancelAtPeriodEnd(), planId);
    }

    private void onInvoicePaid(Invoice invoice) {
        String subscriptionId = subscriptionIdOf(invoice);
        if (subscriptionId == null) {
            return;
        }
        try {
            var subscription = com.stripe.model.Subscription.retrieve(subscriptionId);
            SubscriptionItem item = subscription.getItems().getData().getFirst();
            subscriptionService.renewSubscriptionPeriod(subscriptionId,
                    toInstant(item.getCurrentPeriodStart()), toInstant(item.getCurrentPeriodEnd()));
        } catch (StripeException e) {
            log.error("Could not renew Stripe subscription {}", subscriptionId, e);
        }
    }

    private static String subscriptionIdOf(Invoice invoice) {
        var parent = invoice.getParent();
        if (parent == null || parent.getSubscriptionDetails() == null) {
            return null;
        }
        return parent.getSubscriptionDetails().getSubscription();
    }

    private static SubscriptionStatus mapStatus(String status) {
        return switch (status) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "past_due", "unpaid", "paused" -> SubscriptionStatus.PAST_DUE;
            case "canceled", "incomplete_expired" -> SubscriptionStatus.CANCELED;
            case "incomplete" -> SubscriptionStatus.INCOMPLETE;
            default -> null;
        };
    }

    private static Instant toInstant(Long epochSeconds) {
        return epochSeconds == null ? null : Instant.ofEpochSecond(epochSeconds);
    }
}
