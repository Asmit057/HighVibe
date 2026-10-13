package com.springproject.lovable_clone.dto.subscription;

public record PlanLimitsResponse(
        String planName,
        // int cant handle null but integer can handle null
        Integer maxtokensPerDay,
        Integer maxProjects,
        Boolean unlimitedAi
) {
}
