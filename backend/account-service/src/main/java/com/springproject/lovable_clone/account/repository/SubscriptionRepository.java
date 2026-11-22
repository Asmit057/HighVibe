package com.springproject.lovable_clone.account.repository;

import com.springproject.lovable_clone.account.entity.Subscription;
import com.springproject.lovable_clone.common.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(Long userId, Collection<SubscriptionStatus> statuses);

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}
