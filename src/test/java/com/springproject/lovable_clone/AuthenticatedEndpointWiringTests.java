package com.springproject.lovable_clone;

import com.springproject.lovable_clone.controller.AuthController;
import com.springproject.lovable_clone.controller.BillingController;
import com.springproject.lovable_clone.controller.FileController;
import com.springproject.lovable_clone.controller.UsageController;
import com.springproject.lovable_clone.entity.User;
import com.springproject.lovable_clone.security.AuthUtil;
import com.springproject.lovable_clone.security.JwtAuthFilter;
import com.springproject.lovable_clone.security.WebSecurityConfig;
import com.springproject.lovable_clone.service.AuthService;
import com.springproject.lovable_clone.service.FileService;
import com.springproject.lovable_clone.service.PlanService;
import com.springproject.lovable_clone.service.SubscriptionService;
import com.springproject.lovable_clone.service.UsageService;
import com.springproject.lovable_clone.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AuthController.class,
        UsageController.class,
        BillingController.class,
        FileController.class
})
@Import({WebSecurityConfig.class, JwtAuthFilter.class, AuthUtil.class})
@TestPropertySource(properties = "jwt.secret-key=highvibe-test-secret-0123456789-0123456789-0123456789")
class AuthenticatedEndpointWiringTests {

    private static final String TEST_SECRET = "highvibe-test-secret-0123456789-0123456789-0123456789";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthUtil authUtil;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private UsageService usageService;
    @MockitoBean
    private SubscriptionService subscriptionService;
    @MockitoBean
    private PlanService planService;
    @MockitoBean
    private FileService fileService;

    private String bearerTokenFor(long userId) {
        User user = User.builder()
                .id(userId)
                .username("user-" + userId + "@example.com")
                .build();
        return "Bearer " + authUtil.generateAccessToken(user);
    }

    private String expiredTokenFor(long userId) {
        return signedTokenFor(userId, TEST_SECRET, Date.from(Instant.now().minusSeconds(60)));
    }

    private String wrongSignatureTokenFor(long userId) {
        String otherSecret = "a-different-secret-key-0123456789-0123456789-0123456789";
        return signedTokenFor(userId, otherSecret, Date.from(Instant.now().plusSeconds(600)));
    }

    private String signedTokenFor(long userId, String secret, Date expiration) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("user-" + userId + "@example.com")
                .claim("userId", String.valueOf(userId))
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    @Test
    void getProfile_usesAuthenticatedUsersId() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearerTokenFor(42L)))
                .andExpect(status().isOk());

        verify(userService).getProfile(42L);
    }

    @Test
    void getTodayUsage_usesAuthenticatedUsersId() throws Exception {
        mockMvc.perform(get("/api/usage/today").header("Authorization", bearerTokenFor(42L)))
                .andExpect(status().isOk());

        verify(usageService).getTodayUsageofUser(42L);
    }

    @Test
    void getMySubscription_usesAuthenticatedUsersId() throws Exception {
        mockMvc.perform(get("/api/me/subscription").header("Authorization", bearerTokenFor(42L)))
                .andExpect(status().isOk());

        verify(subscriptionService).getCurrentSubscription(42L);
    }

    @Test
    void getFileTree_usesAuthenticatedUsersId() throws Exception {
        mockMvc.perform(get("/api/projects/7/files").header("Authorization", bearerTokenFor(42L)))
                .andExpect(status().isOk());

        verify(fileService).getFileTree(7L, 42L);
    }

    @Test
    void getProfile_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withoutToken_isAllowed() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void stripeCheckout_isServedAtCorrectPath() throws Exception {
        mockMvc.perform(post("/api/stripe/checkout")
                        .header("Authorization", bearerTokenFor(42L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(subscriptionService).createCheckoutSessionUrl(any(), eq(42L));
    }

    @Test
    void stripeCheckoutTypoPath_isGone() throws Exception {
        mockMvc.perform(post("/api/stripe/checkcout")
                        .header("Authorization", bearerTokenFor(42L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedToken_onProtectedRoute_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/usage/today").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredToken_onProtectedRoute_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/usage/today").header("Authorization", "Bearer " + expiredTokenFor(42L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSignatureToken_onProtectedRoute_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/usage/today").header("Authorization", "Bearer " + wrongSignatureTokenFor(42L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emptyBearerToken_onProtectedRoute_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/usage/today").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedToken_onPublicRoute_doesNotBlockLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Authorization", "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isOk());
    }
}
