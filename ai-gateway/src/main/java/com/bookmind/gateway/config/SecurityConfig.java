package com.bookmind.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public GatewayRateLimitingFilter gatewayRateLimitingFilter() {
        return new GatewayRateLimitingFilter();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, GatewayRateLimitingFilter rateLimitingFilter)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/gateway/health").permitAll()
                .requestMatchers("/api/gateway/providers").permitAll()
                .requestMatchers("/api/gateway/telemetry/**").hasRole("ADMIN")
                .requestMatchers("/api/gateway/**").authenticated()
                .anyRequest().authenticated()
            )
            .httpBasic(httpBasic -> {});

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        String username = System.getenv("GATEWAY_USER");
        String password = System.getenv("GATEWAY_PASSWORD");
        String role = System.getenv("GATEWAY_ROLE");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                "GATEWAY_USER and GATEWAY_PASSWORD must be set for the AI gateway; "
                + "the default Spring Boot user is not acceptable for deployment.");
        }

        UserDetails admin = User.builder()
            .username(username)
            .password(passwordEncoder().encode(password))
            .roles(role != null && !role.isBlank() ? role : "USER")
            .build();

        return new InMemoryUserDetailsManager(admin);
    }
}
