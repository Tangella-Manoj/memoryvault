package io.memoryvault.security;

import io.memoryvault.service.ChromeSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class ChromeSessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Chrome-Token";

    private final ChromeSessionService chromeSessionService;

    public ChromeSessionAuthenticationFilter(ChromeSessionService chromeSessionService) {
        this.chromeSessionService = chromeSessionService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = request.getHeader(HEADER);
            if (token == null || token.isBlank()) {
                token = request.getHeader("X-Chrome-Session");
            }
            if (token != null && !token.isBlank()) {
                chromeSessionService.resolveUserId(token.trim()).ifPresent(userId -> {
                    var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
        }

        filterChain.doFilter(request, response);
    }
}
