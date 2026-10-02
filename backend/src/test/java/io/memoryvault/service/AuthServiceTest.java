package io.memoryvault.service;

import io.memoryvault.domain.PasswordResetToken;
import io.memoryvault.domain.User;
import io.memoryvault.dto.auth.ForgotPasswordRequest;
import io.memoryvault.dto.auth.LoginRequest;
import io.memoryvault.dto.auth.ResetPasswordRequest;
import io.memoryvault.exception.ApiException;
import io.memoryvault.exception.InvalidCredentialsException;
import io.memoryvault.repository.PasswordResetTokenRepository;
import io.memoryvault.repository.RefreshTokenRepository;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private JwtService jwtService;

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordResetTokenRepository,
                passwordEncoder,
                jwtService,
                604800000L,
                "vault.test.com"
        );
    }

    @Test
    void login_userNotFound_throwsExactError() {
        LoginRequest req = new LoginRequest("missing@test.com", "Password@123");
        when(userRepository.findByEmailIgnoreCase("missing@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("No account found with email: missing@test.com")
                .matches(e -> "USER_NOT_FOUND".equals(((InvalidCredentialsException) e).getErrorCode()));
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
        when(userRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Incorrect password. Please check your password or use forgot password.")
                .matches(e -> "INVALID_PASSWORD".equals(((InvalidCredentialsException) e).getErrorCode()));
    }

    @Test
    void forgotPassword_userNotFound_throwsExactError() {
        ForgotPasswordRequest req = new ForgotPasswordRequest("ghost@test.com");
        when(userRepository.findByEmailIgnoreCase("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.forgotPassword(req))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("No account found with email: ghost@test.com")
                .matches(e -> "USER_NOT_FOUND".equals(((InvalidCredentialsException) e).getErrorCode()));
    }

    @Test
    void forgotPassword_and_resetPassword_success() {
        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .passwordHash(passwordEncoder.encode("OldPassword@1"))
                .displayName("Test User")
                .build();

        when(userRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(refreshTokenRepository.findAllByUser(user)).thenReturn(List.of());

        // Step 1: Request code - code must NOT be returned in API response for security
        Map<String, Object> forgotResp = authService.forgotPassword(new ForgotPasswordRequest("user@test.com"));
        assertThat(forgotResp.get("resetCode")).isNull();
        assertThat(forgotResp.get("email")).isEqualTo("user@test.com");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getCodeHash()).isNotNull().hasSize(64); // SHA-256 hex
        assertThat(savedToken.getExpiresAt()).isAfter(Instant.now());

        // Mock repository lookup for reset step
        when(passwordResetTokenRepository.findTopByUser_EmailIgnoreCaseAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                .thenReturn(Optional.of(savedToken));

        // Step 2: Reset password with wrong code
        ResetPasswordRequest badReq = new ResetPasswordRequest("user@test.com", "999999", "NewPassword@123");
        assertThatThrownBy(() -> authService.resetPassword(badReq))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid reset code");

        // Step 3: Mock token with known hash for good code
        // SHA-256 for "123456": 8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92
        PasswordResetToken validToken = PasswordResetToken.builder()
                .user(user)
                .codeHash("8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92")
                .expiresAt(Instant.now().plusSeconds(600))
                .used(false)
                .build();
        when(passwordResetTokenRepository.findTopByUser_EmailIgnoreCaseAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                .thenReturn(Optional.of(validToken));

        ResetPasswordRequest goodReq = new ResetPasswordRequest("user@test.com", "123456", "NewPassword@123");
        authService.resetPassword(goodReq);

        assertThat(validToken.isUsed()).isTrue();
        assertThat(passwordEncoder.matches("NewPassword@123", user.getPasswordHash())).isTrue();
    }
}
