package com.springproject.lovable_clone.account.entity;

import com.springproject.lovable_clone.common.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "subscriptions", indexes = @Index(name = "idx_subscriptions_user", columnList = "user_id, status"))
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    SubscriptionStatus status;

    String stripeCustomerId;

    @Column(unique = true)
    String stripeSubscriptionId;

    Instant currentPeriodStart;
    Instant currentPeriodEnd;

    @Builder.Default
    @Column(nullable = false)
    Boolean cancelAtPeriodEnd = false;

    @CreationTimestamp
    Instant createdAt;
    @UpdateTimestamp
    Instant updatedAt;
}
