package io.memoryvault.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies per-route request-rate limits using in-memory Bucket4j buckets. Login is limited
 * per client IP (it runs before authentication); save and import are limited per authenticated
 * user id. On exceeding the limit, responds 429 with a {@code Retry-After} header instead of
 * letting the request reach the controller.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> saveBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> importBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        RuleMatch match = matchRule(request);
        if (match == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = match.buckets.computeIfAbsent(match.key, k -> newBucket(match.capacity, match.period));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
        } else {
            long waitSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(waitSeconds));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":\"ERROR\",\"message\":\"Rate limit exceeded, retry after " + waitSeconds + "s\"}");
        }
    }

    private RuleMatch matchRule(HttpServletRequest request) {
        String path = request.getRequestURI();
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        if (method == HttpMethod.POST && path.equals("/api/auth/login")) {
            String ip = clientIp(request);
            return new RuleMatch(loginBuckets, ip, 5, Duration.ofMinutes(1));
        }
        if (method == HttpMethod.POST && path.equals("/api/vault/save")) {
            Long userId = currentUserIdOrNull();
            if (userId == null) return null;
            return new RuleMatch(saveBuckets, String.valueOf(userId), 30, Duration.ofMinutes(1));
        }
        if (method == HttpMethod.POST && path.equals("/api/vault/import/json")) {
            Long userId = currentUserIdOrNull();
            if (userId == null) return null;
            return new RuleMatch(importBuckets, String.valueOf(userId), 3, Duration.ofHours(1));
        }
        return null;
    }

    private Bucket newBucket(int capacity, Duration period) {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(capacity, period));
        return Bucket.builder().addLimit(limit).build();
    }

    private Long currentUserIdOrNull() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            return null;
        }
        return userId;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record RuleMatch(ConcurrentHashMap<String, Bucket> buckets, String key, int capacity, Duration period) {
    }
}
