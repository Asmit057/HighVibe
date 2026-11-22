package com.springproject.lovable_clone.account.config;

import com.springproject.lovable_clone.account.entity.Plan;
import com.springproject.lovable_clone.account.repository.PlanRepository;
import com.stripe.Stripe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds the default plan catalog and attaches Stripe price ids from configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanCatalogInitializer implements ApplicationRunner {

    private final PlanRepository planRepository;
    private final AccountProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.billing().stripeEnabled()) {
            Stripe.apiKey = properties.billing().stripeSecretKey();
        } else {
            log.info("Stripe is not configured; billing runs in {} mode",
                    properties.billing().mockMode() ? "mock" : "disabled");
        }

        List<Plan> defaults = List.of(
                plan("Free", 0, 3, 50_000, 1, false, 0),
                plan("Pro", 2500, 25, 1_000_000, 3, false, 1),
                plan("Business", 5000, null, null, 10, true, 2));

        for (Plan template : defaults) {
            Plan plan = planRepository.findByNameIgnoreCase(template.getName())
                    .orElseGet(() -> planRepository.save(template));
            String priceId = properties.billing().stripePriceIds().get(plan.getName());
            if (priceId != null && !priceId.isBlank() && !priceId.equals(plan.getStripePriceId())) {
                plan.setStripePriceId(priceId);
            }
        }
    }

    private static Plan plan(String name, int priceCents, Integer maxProjects, Integer maxTokens,
                             Integer maxPreviews, boolean unlimitedAi, int order) {
        return Plan.builder()
                .name(name)
                .priceCents(priceCents)
                .maxProjects(maxProjects)
                .maxTokensPerDay(maxTokens)
                .maxPreviews(maxPreviews)
                .unlimitedAi(unlimitedAi)
                .active(true)
                .sortOrder(order)
                .build();
    }
}
