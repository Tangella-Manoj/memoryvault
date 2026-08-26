package io.memoryvault.security;

import io.memoryvault.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long userId, String... roles) {
        var authorities = List.of(roles).stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void regularUser_actingOnOwnId_isAllowed() {
        authenticateAs(1L, "USER");

        SecurityUtil.requireSelfOrAdmin(1L);
        // no exception => allowed
    }

    @Test
    void regularUser_actingOnAnotherUsersId_isForbidden() {
        authenticateAs(1L, "USER");

        assertThatThrownBy(() -> SecurityUtil.requireSelfOrAdmin(2L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void admin_actingOnAnotherUsersId_isAllowed() {
        authenticateAs(1L, "ADMIN");

        SecurityUtil.requireSelfOrAdmin(999L);
        // no exception => admin bypass works
    }
}
