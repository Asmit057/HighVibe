package com.springproject.lovable_clone.intelligence.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

/** Daily token counter per user (UTC day). */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "usage_logs",
        uniqueConstraints = @UniqueConstraint(name = "uk_usage_logs_user_date", columnNames = {"user_id", "usage_date"}))
public class UsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "user_id", nullable = false)
    Long userId;

    @Column(name = "usage_date", nullable = false)
    LocalDate date;

    @Column(nullable = false)
    Long tokensUsed;

    @Column(nullable = false)
    Integer generations;

    @Version
    Long version;
}
