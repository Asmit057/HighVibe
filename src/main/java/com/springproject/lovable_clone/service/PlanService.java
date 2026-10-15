package com.springproject.lovable_clone.service;

import com.springproject.lovable_clone.dto.subscription.PlanResponse;

import java.util.List;

public interface PlanService {
    List<PlanResponse> getAllActivePlans();
}
