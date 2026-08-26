package io.memoryvault.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.memoryvault.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Without this, Spring Security's default behavior for a request that reaches
 * {@code anyRequest().authenticated()} with no valid credentials is a bare 403 —
 * indistinguishable from "you're logged in but not allowed." This returns the correct
 * 401 (missing/invalid/expired token — retry with credentials) in the app's normal
 * {@link ApiResponse} envelope, keeping 403 meaningful for actual authorization failures
 * (see {@code SecurityUtil.requireSelfOrAdmin}).
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                objectMapper.writeValueAsString(ApiResponse.error("Authentication required or token is invalid/expired")));
    }
}
