package com.springproject.lovable_clone.service.impl;

import com.springproject.lovable_clone.dto.subscription.PlanLimitsResponse;
import com.springproject.lovable_clone.dto.subscription.UsageTodayResponse;
import com.springproject.lovable_clone.service.UsageService;
import org.springframework.stereotype.Service;

@Service
public class UsageServiceImpl implements UsageService {
    @Override
    public UsageTodayResponse getTodayUsageofUser(Long userId) {
        return null;
    }

    @Override
    public PlanLimitsResponse getCurrentSubscriptionLimitsOfUser(Long userId) {
        return null;
    }
}
