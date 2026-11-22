package com.springproject.lovable_clone.account.service;

import com.springproject.lovable_clone.account.dto.BillingDtos.SubscriptionResponse;
import com.springproject.lovable_clone.account.entity.Plan;
import com.springproject.lovable_clone.account.entity.Subscription;
import com.springproject.lovable_clone.account.entity.User;
import com.springproject.lovable_clone.account.repository.SubscriptionRepository;
import com.springproject.lovable_clone.account.repository.UserRepository;
import com.springproject.lovable_clone.common.dto.PlanDto;
import com.springproject.lovable_clone.common.enums.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Subscription lifecycle. A user without a live subscription is on the Free plan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    /** Statuses that still grant the plan (PAST_DUE keeps access during the grace period). */
    private static final Set<SubscriptionStatus> LIVE = EnumSet.of(
            SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING, SubscriptionStatus.PAST_DUE);

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PlanService planService;

    @Transactional(readOnly = true)
    public Optional<Subscription> findLiveSubscription(Long userId) {
        return subscriptionRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, LIVE);
    }

    @Transactional(readOnly = true)
    public Plan getCurrentPlan(Long userId) {
        return findLiveSubscription(userId).map(Subscription::getPlan).orElseGet(planService::getFreePlan);
    }

    @Transactional(readOnly = true)
    public PlanDto getCurrentPlanDto(Long userId) {
        return PlanService.toDto(getCurrentPlan(userId));
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getCurrentSubscription(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        boolean portal = user.getStripeCustomerId() != null;
        return findLiveSubscription(userId)
                .map(s -> new SubscriptionResponse(PlanService.toResponse(s.getPlan()), s.getStatus().name(),
                        s.getCurrentPeriodEnd(), Boolean.TRUE.equals(s.getCancelAtPeriodEnd()), portal))
                .orElseGet(() -> new SubscriptionResponse(PlanService.toResponse(planService.getFreePlan()),
                        SubscriptionStatus.ACTIVE.name(), null, false, portal));
    }

    /** Starts a paid subscription (from Stripe checkout or mock checkout), replacing any live one. */
    @Transactional
    public Subscription activateSubscription(Long userId, Long planId, String stripeSubscriptionId,
                                             String stripeCustomerId, Instant periodStart, Instant periodEnd) {
        if (stripeSubscriptionId != null) {
            Optional<Subscription> existing = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        findLiveSubscription(userId).ifPresent(previous -> previous.setStatus(SubscriptionStatus.CANCELED));

        Instant start = periodStart != null ? periodStart : Instant.now();
        Instant end = periodEnd != null ? periodEnd : start.plus(30, ChronoUnit.DAYS);
        Subscription subscription = Subscription.builder()
                .user(userRepository.getReferenceById(userId))
                .plan(planService.getPlan(planId))
                .status(SubscriptionStatus.ACTIVE)
                .stripeSubscriptionId(stripeSubscriptionId)
                .stripeCustomerId(stripeCustomerId)
                .currentPeriodStart(start)
                .currentPeriodEnd(end)
                .cancelAtPeriodEnd(false)
                .build();
        log.info("Activated plan {} for user {}", planId, userId);
        return subscriptionRepository.save(subscription);
    }

    /** Downgrades to Free by ending the live subscription. */
    @Transactional
    public void cancelLiveSubscription(Long userId) {
        findLiveSubscription(userId).ifPresent(s -> s.setStatus(SubscriptionStatus.CANCELED));
    }

    @Transactional
    public void updateSubscription(String stripeSubscriptionId, SubscriptionStatus status, Instant periodStart,
                                   Instant periodEnd, Boolean cancelAtPeriodEnd, Long planId) {
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId).ifPresentOrElse(s -> {
            s.setStatus(status);
            if (periodStart != null) s.setCurrentPeriodStart(periodStart);
            if (periodEnd != null) s.setCurrentPeriodEnd(periodEnd);
            if (cancelAtPeriodEnd != null) s.setCancelAtPeriodEnd(cancelAtPeriodEnd);
            if (planId != null && !planId.equals(s.getPlan().getId())) s.setPlan(planService.getPlan(planId));
        }, () -> log.warn("Stripe subscription {} is unknown, ignoring update", stripeSubscriptionId));
    }

    @Transactional
    public void cancelSubscription(String stripeSubscriptionId) {
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .ifPresent(s -> s.setStatus(SubscriptionStatus.CANCELED));
    }

    @Transactional
    public void renewSubscriptionPeriod(String stripeSubscriptionId, Instant periodStart, Instant periodEnd) {
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(s -> {
            s.setStatus(SubscriptionStatus.ACTIVE);
            s.setCurrentPeriodStart(periodStart);
            s.setCurrentPeriodEnd(periodEnd);
        });
    }

    @Transactional
    public void markSubscriptionPastDue(String stripeSubscriptionId) {
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .ifPresent(s -> s.setStatus(SubscriptionStatus.PAST_DUE));
    }
}
