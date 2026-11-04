package com.bookassistant.config;

import com.bookassistant.entity.User;
import com.bookassistant.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, RateLimitingFilter rateLimitingFilter,
                                           SecurityHeadersFilter securityHeadersFilter,
                                           SecurityContextPopulatingFilter securityContextFilter) throws Exception {
        http
            .addFilterBefore(securityContextFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class)
            .csrf(csrf -> csrf
                // CSRF is enforced on all state-changing endpoints. Public GET routes
                // remain permitAll() via authorizeHttpRequests below.
                .ignoringRequestMatchers("/oauth2/**")
            )
            .sessionManagement(session -> session
                .sessionFixation().migrateSession()
                .maximumSessions(1)
            )
            .authorizeHttpRequests(authz -> authz
                // Public routes
                .requestMatchers("/", "/register", "/login", "/logout", "/error").permitAll()
                .requestMatchers("/oauth2/**").permitAll()
                .requestMatchers("/language/**").permitAll()
                .requestMatchers("/styles.css", "/js/**", "/images/**", "/favicon.ico").permitAll()
                // Actuator restricted to authenticated users
                .requestMatchers("/actuator/**").authenticated()
                // Protected routes — require authenticated session
                .requestMatchers("/app", "/qa", "/summary/**", "/reality-check", "/concept-map",
                    "/risk-flags", "/semantic-search", "/upload", "/books/**", "/accounts/**",
                    "/settings/**", "/upload/progress/**").authenticated()
                .anyRequest().authenticated()
            );

        return http.build();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> {
            User user = userRepository.findByEmail(username);
            if (user == null) {
                throw new UsernameNotFoundException("User not found: " + username);
            }
            return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .roles("USER")
                .build();
        };
    }
}



