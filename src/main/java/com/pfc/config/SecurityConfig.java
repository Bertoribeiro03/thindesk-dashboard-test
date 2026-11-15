package com.pfc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfc.api.ApiError;
import com.pfc.api.ErrorHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean public UserDetailsService users(ThindeskProperties properties, PasswordEncoder encoder) {
        if (!properties.securityEnabled()) return new InMemoryUserDetailsManager();
        if (properties.username().isBlank() || properties.password().isBlank()) throw new IllegalStateException("Configure THINDESK_USERNAME e THINDESK_PASSWORD para habilitar o login.");
        return new InMemoryUserDetailsManager(User.withUsername(properties.username()).password(encoder.encode(properties.password())).roles("ADMIN").build());
    }
    @Bean SecurityFilterChain security(HttpSecurity http, ThindeskProperties properties, ObjectMapper mapper) throws Exception {
        http.authorizeHttpRequests(rules -> {
            rules.requestMatchers("/login", "/error", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll();
            if (properties.securityEnabled()) rules.anyRequest().authenticated(); else rules.anyRequest().permitAll();
        });
        http.formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).permitAll());
        http.logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        http.exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, exception) -> {
                if (ErrorHandlers.path(request).startsWith("/api/")) json(response, mapper, 401, "Autenticação necessária.");
                else response.sendRedirect(request.getContextPath() + "/login");
            })
            .accessDeniedHandler((request, response, exception) -> {
                if (ErrorHandlers.path(request).startsWith("/api/")) json(response, mapper, 403, "Solicitação não autorizada.");
                else response.sendError(403);
            }));
        return http.build();
    }
    private static void json(HttpServletResponse response, ObjectMapper mapper, int status, String message) throws java.io.IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), new ApiError(status, "HTTP_ERROR", message).body());
    }
}
