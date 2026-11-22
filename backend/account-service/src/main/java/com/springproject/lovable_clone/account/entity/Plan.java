package com.springproject.lovable_clone.account.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/** A subscription tier. Null limits mean unlimited. */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true)
    String name;

    String stripePriceId;

    // monthly price in cents, 0 = free
    @Column(nullable = false)
    Integer priceCents;

    Integer maxProjects;
    Integer maxTokensPerDay;
    // live previews that may run at the same time
    Integer maxPreviews;

    @Column(nullable = false)
    Boolean unlimitedAi;

    @Column(nullable = false)
    Boolean active;

    @Column(nullable = false)
    Integer sortOrder;

    public boolean isFree() {
        return priceCents == null || priceCents == 0;
    }
}
