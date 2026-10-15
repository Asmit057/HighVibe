package com.springproject.lovable_clone.service;

import com.springproject.lovable_clone.dto.subscription.CheckoutRequest;
import com.springproject.lovable_clone.dto.subscription.CheckoutResponse;
import com.springproject.lovable_clone.dto.subscription.PortalResponse;
import com.springproject.lovable_clone.dto.subscription.SubscriptionResponse;
import org.jspecify.annotations.Nullable;

public interface SubscriptionService {
     SubscriptionResponse getCurrentSubscription(Long userId);

     CheckoutResponse createCheckoutSessionUrl(CheckoutRequest request, Long userId);

      PortalResponse openCustomerPortal(Long userId);
}
