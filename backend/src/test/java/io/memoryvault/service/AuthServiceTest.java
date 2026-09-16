package io.memoryvault.service;

import io.memoryvault.domain.User;
import io.memoryvault.dto.auth.AuthResponse;
import io.memoryvault.dto.auth.ForgotPasswordRequest;
import io.memoryvault.dto.auth.LoginRequest;
import io.memoryvault.dto.auth.ResetPasswordRequest;
import io.memoryvault.exception.ApiException;
import io.memoryvault.exception.InvalidCredentialsException;
import io.memoryvault.repository.RefreshTokenRepository;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService,
                604800000L,
                "vault.test.com"
        );
    }

    @Test
    void login_userNotFound_throwsExactError() {
        LoginRequest req = new LoginRequest("missing@test.com", "Password@123");
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("No account found with email: missing@test.com");
    }

    @Test
    void login_wrongPassword_throwsExactError() {
        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .passwordHash(passwordEncoder.encode("CorrectPassword@1"))
                .displayName("Test User")
                .build();

        LoginRequest req = new LoginRequest("user@test.com", "WrongPassword@1");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Incorrect password. Please check your password or use forgot password.");
    }

    @Test
    void forgotPassword_userNotFound_throwsExactError() {
        ForgotPasswordRequest req = new ForgotPasswordRequest("ghost@test.com");
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.forgotPassword(req))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("No account found with email: ghost@test.com");
    }

    @Test
    void forgotPassword_and_resetPassword_success() {
        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .passwordHash(passwordEncoder.encode("OldPassword@1"))
                .displayName("Test User")
                .build();

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(refreshTokenRepository.findAllByUser(user)).thenReturn(List.of());

        // Step 1: Request code
        Map<String, Object> forgotResp = authService.forgotPassword(new ForgotPasswordRequest("user@test.com"));
        String code = (String) forgotResp.get("resetCode");
        assertThat(code).isNotNull().hasSize(6);

        // Step 2: Reset password with wrong code
        ResetPasswordRequest badReq = new ResetPasswordRequest("user@test.com", "999999", "NewPassword@123");
        assertThatThrownBy(() -> authService.resetPassword(badReq))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid reset code");

        // Step 3: Reset password with right code
        ResetPasswordRequest goodReq = new ResetPasswordRequest("user@test.com", code, "NewPassword@123");
        authService.resetPassword(goodReq);

        assertThat(passwordEncoder.matches("NewPassword@123", user.getPasswordHash())).isTrue();
    }
}
