package com.springproject.lovable_clone.dto.subscription;

public record PlanResponse(
        Long id,

        String name,
        Integer maxProjects,
        // if we use int in place of Integer we get error in database bcz it is entity
        Integer maxTokensPerDay,// this field tell that how many times u run ur application


        Boolean unlimitedAi,
        String price
) {
}
