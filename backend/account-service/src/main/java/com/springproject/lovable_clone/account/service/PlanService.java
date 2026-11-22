package com.springproject.lovable_clone.account.service;

import com.springproject.lovable_clone.account.dto.BillingDtos.PlanResponse;
import com.springproject.lovable_clone.account.entity.Plan;
import com.springproject.lovable_clone.account.repository.PlanRepository;
import com.springproject.lovable_clone.common.dto.PlanDto;
import com.springproject.lovable_clone.common.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

    public static final String FREE_PLAN = "Free";

    private final PlanRepository planRepository;

    public List<PlanResponse> getActivePlans() {
        return planRepository.findAllByActiveTrueOrderBySortOrderAsc().stream()
                .map(PlanService::toResponse)
                .toList();
    }

    public Plan getPlan(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planId.toString()));
    }

    public Plan getFreePlan() {
        return planRepository.findByNameIgnoreCase(FREE_PLAN)
                .orElseThrow(() -> new IllegalStateException("The Free plan is missing from the catalog"));
    }

    public static PlanResponse toResponse(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getPriceCents(),
                formatPrice(plan),
                plan.getMaxProjects(),
                plan.getMaxTokensPerDay(),
                plan.getMaxPreviews(),
                plan.getUnlimitedAi(),
                !plan.isFree());
    }

    public static PlanDto toDto(Plan plan) {
        return new PlanDto(
                plan.getId(),
                plan.getName(),
                plan.getMaxProjects(),
                plan.getMaxTokensPerDay(),
                plan.getMaxPreviews(),
                plan.getUnlimitedAi(),
                formatPrice(plan));
    }

    static String formatPrice(Plan plan) {
        if (plan.isFree()) {
            return "Free";
        }
        int cents = plan.getPriceCents();
        return cents % 100 == 0
                ? "$" + (cents / 100) + "/mo"
                : String.format("$%d.%02d/mo", cents / 100, cents % 100);
    }
}
