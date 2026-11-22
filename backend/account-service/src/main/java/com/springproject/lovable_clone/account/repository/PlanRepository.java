package com.springproject.lovable_clone.account.repository;

import com.springproject.lovable_clone.account.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    List<Plan> findAllByActiveTrueOrderBySortOrderAsc();

    Optional<Plan> findByNameIgnoreCase(String name);

    Optional<Plan> findByStripePriceId(String stripePriceId);
}
