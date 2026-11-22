package com.springproject.lovable_clone.account.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/** Stripe event ids already handled, so redelivered webhooks run only once. */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "processed_webhook_events")
public class ProcessedWebhookEvent {
    @Id
    String eventId;
    String eventType;
    Instant processedAt;
}
