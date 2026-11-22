package com.springproject.lovable_clone.common.dto;

/**
 * A subscription plan as seen by other services. Null limits mean unlimited.
 */
public record PlanDto(
        Long id,
        String name,
        Integer maxProjects,
        Integer maxTokensPerDay,
        Integer maxPreviews,
        Boolean unlimitedAi,
        String price
) {
}
