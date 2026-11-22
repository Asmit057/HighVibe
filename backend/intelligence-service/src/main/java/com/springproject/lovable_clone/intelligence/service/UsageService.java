package com.springproject.lovable_clone.intelligence.service;

import com.springproject.lovable_clone.common.dto.PlanDto;
import com.springproject.lovable_clone.common.error.UsageLimitExceededException;
import com.springproject.lovable_clone.intelligence.client.AccountClient;
import com.springproject.lovable_clone.intelligence.dto.ChatDtos.UsageTodayResponse;
import com.springproject.lovable_clone.intelligence.entity.UsageLog;
import com.springproject.lovable_clone.intelligence.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Daily token metering against the caller's plan (from account-service).
 */
@Service
@RequiredArgsConstructor
public class UsageService {

    private final UsageLogRepository usageLogRepository;
    private final AccountClient accountClient;
    private final TransactionTemplate tx;

    public UsageTodayResponse getTodayUsage(Long userId) {
        PlanDto plan = accountClient.getCurrentPlan();
        UsageLog log = usageLogRepository.findByUserIdAndDate(userId, today()).orElse(null);
        long used = log == null ? 0 : log.getTokensUsed();
        int generations = log == null ? 0 : log.getGenerations();
        boolean unlimited = Boolean.TRUE.equals(plan.unlimitedAi()) || plan.maxTokensPerDay() == null;
        return new UsageTodayResponse(plan.name(), used, unlimited ? null : plan.maxTokensPerDay(), unlimited, generations);
    }

    /** Throws 429 when today's tokens already reached the plan limit. Requires the caller's JWT. */
    public void checkDailyTokens(Long userId) {
        UsageTodayResponse usage = getTodayUsage(userId);
        if (!usage.unlimited() && usage.tokensUsed() >= usage.tokenLimit()) {
            throw new UsageLimitExceededException("You used all " + usage.tokenLimit() + " AI tokens of your "
                    + usage.planName() + " plan today. Upgrade or come back tomorrow.");
        }
    }

    public void recordTokens(Long userId, long tokens) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                tx.executeWithoutResult(status -> {
                    UsageLog log = usageLogRepository.findByUserIdAndDate(userId, today())
                            .orElseGet(() -> UsageLog.builder().userId(userId).date(today()).tokensUsed(0L).generations(0).build());
                    log.setTokensUsed(log.getTokensUsed() + tokens);
                    log.setGenerations(log.getGenerations() + 1);
                    usageLogRepository.save(log);
                });
                return;
            } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
                // concurrent update of the same day row: retry with fresh state
            }
        }
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
