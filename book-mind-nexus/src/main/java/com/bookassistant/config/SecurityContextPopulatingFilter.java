package com.bookassistant.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Populates the Spring Security {@code SecurityContext} from the manually-managed
 * HTTP session attributes ({@code userId}, {@code userEmail}) set by
 * {@code AuthController}.
 * <p>
 * Without this filter, Spring Security's {@code authenticated()} authorization
 * guards and session-fixation protection never reflect the actual login state,
 * because the application performs manual session-based authentication instead of
 * {@code formLogin}/{@code oauth2Login}.
 */
@Component
public class SecurityContextPopulatingFilter extends OncePerRequestFilter {

    private static final String SESSION_USER_ID = "userId";
    private static final String SESSION_USER_EMAIL = "userEmail";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean contextPopulated = false;
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object userId = session.getAttribute(SESSION_USER_ID);
                String userEmail = (String) session.getAttribute(SESSION_USER_EMAIL);
                if (userId != null && userEmail != null
                        && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userEmail, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    contextPopulated = true;
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            if (contextPopulated) {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
