package com.springproject.lovable_clone.common.security;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SharedSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthUtil authUtil(@Value("${jwt.secret-key}") String jwtSecretKey) {
        return new AuthUtil(jwtSecretKey);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthFilter jwtAuthFilter(AuthUtil authUtil) {
        return new JwtAuthFilter(authUtil);
    }

    /** The filter runs inside the security chain only, not a second time as a plain servlet filter. */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter jwtAuthFilter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(jwtAuthFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public JsonSecurityErrorHandler jsonSecurityErrorHandler() {
        return new JsonSecurityErrorHandler();
    }

    /** Forwards the caller JWT on service-to-service Feign calls. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RequestInterceptor.class)
    static class FeignTokenRelayConfiguration {
        @Bean
        public RequestInterceptor jwtRelayInterceptor() {
            return template -> {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.getCredentials() instanceof String token) {
                    template.header("Authorization", "Bearer " + token);
                }
            };
        }
    }
}
