package io.memoryvault.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter();
    }

    @Test
    void nonRateLimitedRoute_passesThroughImmediately() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/actuator/health");
        when(request.getMethod()).thenReturn("GET");

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void loginRoute_withinLimit_allowsRequest() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Forwarded-For")).thenReturn("103.21.244.2");

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void loginRoute_exceedingFiveAttempts_responds429WithRetryAfter() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getMethod()).thenReturn("POST");
        // Using a unique IP to avoid bucket contamination
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.99");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        lenient().when(response.getWriter()).thenReturn(pw);

        // Exhaust all 5 tokens
        for (int i = 0; i < 5; i++) {
            rateLimitFilter.doFilterInternal(request, response, filterChain);
        }
        verify(filterChain, times(5)).doFilter(request, response);

        // 6th attempt must be rejected with 429
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(429);
        verify(response).setHeader(eq("Retry-After"), anyString());
        verify(filterChain, times(5)).doFilter(request, response); // not called 6th time
        assertThat(sw.toString()).contains("Rate limit exceeded");
    }

    @Test
    void clientIp_picksOutermostProxyFromChain() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getMethod()).thenReturn("POST");
        // Chain: spoofed_client, inner_proxy, outermost_trusted_proxy
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 10.0.0.1, 198.51.100.42");

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void clientIp_withTrailingWhitespaceOrEmptyEntries_fallsBackProperly() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1,   ");

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void clientIp_withoutForwardedHeader_usesRemoteAddr() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
