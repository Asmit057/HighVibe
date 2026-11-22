package com.springproject.lovable_clone.account;

import com.springproject.lovable_clone.account.controller.AuthController;
import com.springproject.lovable_clone.account.controller.BillingController;
import com.springproject.lovable_clone.account.controller.InternalAccountController;
import com.springproject.lovable_clone.account.dto.AuthDtos.AuthResponse;
import com.springproject.lovable_clone.account.dto.AuthDtos.UserProfileResponse;
import com.springproject.lovable_clone.account.security.AccountSecurityConfig;
import com.springproject.lovable_clone.account.service.*;
import com.springproject.lovable_clone.common.error.SharedExceptionAutoConfiguration;
import com.springproject.lovable_clone.common.security.AuthUtil;
import com.springproject.lovable_clone.common.security.SharedSecurityAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the HTTP and security wiring of account-service: public vs protected routes,
 * JWT handling, request validation and the error contract.
 */
@WebMvcTest(controllers = {AuthController.class, BillingController.class, InternalAccountController.class})
@Import(AccountSecurityConfig.class)
@ImportAutoConfiguration({SharedSecurityAutoConfiguration.class, SharedExceptionAutoConfiguration.class})
class AccountEndpointSecurityTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthUtil authUtil;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private PlanService planService;
    @MockitoBean
    private SubscriptionService subscriptionService;
    @MockitoBean
    private BillingService billingService;

    private String bearer(long userId) {
        return "Bearer " + authUtil.generateAccessToken(userId, "user" + userId + "@example.com", "User", Duration.ofMinutes(5));
    }

    @Test
    void signup_isPublic_andReturnsCreated() throws Exception {
        when(authService.signup(any())).thenReturn(new AuthResponse("t", "r", 1800, new UserProfileResponse(1L, "ada@example.com", "Ada")));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ada@example.com\",\"name\":\"Ada\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("t"))
                .andExpect(jsonPath("$.user.username").value("ada@example.com"));
    }

    @Test
    void signup_withInvalidBody_returnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"not-an-email\",\"name\":\"\",\"password\":\"1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Input validations failed"))
                .andExpect(jsonPath("$.errors").isArray());
        verifyNoInteractions(authService);
    }

    @Test
    void me_withoutToken_isUnauthorizedJson() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("UNAUTHORIZED"));
    }

    @Test
    void me_usesTheAuthenticatedUser() throws Exception {
        when(userService.getProfile(42L)).thenReturn(new UserProfileResponse(42L, "user42@example.com", "User"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(42L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));
        verify(userService).getProfile(42L);
    }

    @Test
    void malformedToken_onProtectedRoute_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/billing/subscription").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedToken_doesNotBlockPublicLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Authorization", "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ada@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void planCatalog_isPublic() throws Exception {
        when(planService.getActivePlans()).thenReturn(List.of());
        mockMvc.perform(get("/api/billing/plans")).andExpect(status().isOk());
    }

    @Test
    void checkout_isForTheCaller() throws Exception {
        mockMvc.perform(post("/api/billing/checkout")
                        .header("Authorization", bearer(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":2}"))
                .andExpect(status().isOk());
        verify(billingService).createCheckout(7L, 2L);
    }

    @Test
    void internalApi_requiresToken() throws Exception {
        mockMvc.perform(get("/internal/v1/billing/current-plan")).andExpect(status().isUnauthorized());
    }
}
