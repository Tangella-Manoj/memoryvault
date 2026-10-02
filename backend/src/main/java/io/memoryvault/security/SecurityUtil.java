package io.memoryvault.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            throw new io.memoryvault.exception.ApiException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Authentication required"
            );
        }
        return userId;
    }

    public static boolean currentUserIsAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    /**
     * Verifies the caller is either the owner of {@code targetUserId} or an admin.
     *
     * @param targetUserId the user id the caller is attempting to act on
     * @throws io.memoryvault.exception.ApiException with 403 FORBIDDEN if neither condition holds
     */
    public static void requireSelfOrAdmin(Long targetUserId) {
        if (currentUserIsAdmin()) {
            return;
        }
        if (!currentUserId().equals(targetUserId)) {
            throw new io.memoryvault.exception.ApiException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "You do not have permission to act on this user's data"
            );
        }
    }
}
