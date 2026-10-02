package io.memoryvault.service;

import io.memoryvault.domain.PasswordResetToken;
import io.memoryvault.domain.RefreshToken;
import io.memoryvault.domain.User;
import io.memoryvault.dto.auth.AuthResponse;
import io.memoryvault.dto.auth.ForgotPasswordRequest;
import io.memoryvault.dto.auth.LoginRequest;
import io.memoryvault.dto.auth.RegisterRequest;
import io.memoryvault.dto.auth.ResetPasswordRequest;
import io.memoryvault.exception.ApiException;
import io.memoryvault.exception.EmailAlreadyRegisteredException;
import io.memoryvault.exception.InvalidCredentialsException;
import io.memoryvault.exception.InvalidRefreshTokenException;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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

    // ─── REGISTER TESTS ────────────────────────────────────────────────────────

    @Test
    void register_success_normalizesEmailAndIssuesTokens() {
        RegisterRequest req = new RegisterRequest("  NewUser@Test.COM  ", "ValidPass123", "New User");
        when(userRepository.existsByEmailIgnoreCase("newuser@test.com")).thenReturn(false);

        User savedUser = User.builder()
                .id(10L)
                .email("newuser@test.com")
                .passwordHash("hashed")
                .displayName("New User")
                .vaultEmail("12345678@vault.test.com")
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateAccessToken(eq(10L), eq("newuser@test.com"), anyString())).thenReturn("mock-access-token");

        AuthResponse resp = authService.register(req);

        assertThat(resp.userId()).isEqualTo(10L);
        assertThat(resp.email()).isEqualTo("newuser@test.com");
        assertThat(resp.accessToken()).isEqualTo("mock-access-token");
        assertThat(resp.refreshToken()).isNotBlank();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("newuser@test.com");
        assertThat(userCaptor.getValue().getVaultEmail()).endsWith("@vault.test.com");
        assertThat(passwordEncoder.matches("ValidPass123", userCaptor.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void register_duplicateEmail_throwsEmailAlreadyRegisteredException() {
        RegisterRequest req = new RegisterRequest("existing@test.com", "ValidPass123", "Existing User");
        when(userRepository.existsByEmailIgnoreCase("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageContaining("existing@test.com");

        verify(userRepository, never()).save(any());
    }

    // ─── LOGIN TESTS ───────────────────────────────────────────────────────────

    @Test
    void login_success_withCaseInsensitiveAndTrimmedEmail() {
        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .passwordHash(passwordEncoder.encode("SecretPass1"))
                .displayName("Test User")
                .build();

        LoginRequest req = new LoginRequest("  USER@TEST.COM  ", "SecretPass1");
        when(userRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(eq(1L), eq("user@test.com"), anyString())).thenReturn("jwt-123");

        AuthResponse resp = authService.login(req);

        assertThat(resp.userId()).isEqualTo(1L);
        assertThat(resp.accessToken()).isEqualTo("jwt-123");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
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

    // ─── REFRESH TOKEN TESTS ───────────────────────────────────────────────────

    @Test
    void refresh_success_rotatesTokens() {
        User user = User.builder()
                .id(2L)
                .email("refresh@test.com")
                .displayName("Refresh User")
                .build();

        RefreshToken existing = RefreshToken.builder()
                .id(100L)
                .user(user)
                .tokenHash("some-hash")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));
        when(jwtService.generateAccessToken(eq(2L), eq("refresh@test.com"), anyString())).thenReturn("new-access-token");

        AuthResponse resp = authService.refresh("raw-refresh-token");

        assertThat(resp.accessToken()).isEqualTo("new-access-token");
        assertThat(existing.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class)); // 1 to revoke existing, 1 to save new
    }

    @Test
    void refresh_revokedToken_throwsInvalidRefreshTokenException() {
        RefreshToken revokedToken = RefreshToken.builder()
                .user(User.builder().id(1L).build())
                .revoked(true)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authService.refresh("revoked-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_expiredToken_throwsInvalidRefreshTokenException() {
        RefreshToken expiredToken = RefreshToken.builder()
                .user(User.builder().id(1L).build())
                .revoked(false)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refresh("expired-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_tokenNotFound_throwsInvalidRefreshTokenException() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("ghost-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    // ─── LOGOUT TESTS ──────────────────────────────────────────────────────────

    @Test
    void logout_existingToken_revokesIt() {
        RefreshToken token = RefreshToken.builder()
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        authService.logout("raw-token");

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void logout_nonExistentToken_isGracefulNoOp() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        authService.logout("non-existent-token");

        verify(refreshTokenRepository, never()).save(any());
    }

    // ─── FORGOT & RESET PASSWORD TESTS ─────────────────────────────────────────

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

        // Step 1: Request code - verify code generation
        Map<String, Object> forgotResp = authService.forgotPassword(new ForgotPasswordRequest("  USER@TEST.COM  "));
        assertThat(forgotResp.get("resetCode")).isNotNull();
        assertThat(forgotResp.get("resetCode").toString()).hasSize(6);
        assertThat(forgotResp.get("email")).isEqualTo("user@test.com");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getCodeHash()).isNotNull().hasSize(64); // SHA-256 hex
        assertThat(savedToken.getExpiresAt()).isAfter(Instant.now());

        // Step 2: Reset password when no token requested
        when(passwordResetTokenRepository.findTopByUser_EmailIgnoreCaseAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                .thenReturn(Optional.empty());
        ResetPasswordRequest noTokenReq = new ResetPasswordRequest("user@test.com", "123456", "NewPassword@123");
        assertThatThrownBy(() -> authService.resetPassword(noTokenReq))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Reset code has expired or was not requested");

        // Step 3: Reset password when token is expired
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .user(user)
                .codeHash("dummy")
                .expiresAt(Instant.now().minusSeconds(10))
                .used(false)
                .build();
        when(passwordResetTokenRepository.findTopByUser_EmailIgnoreCaseAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                .thenReturn(Optional.of(expiredToken));
        assertThatThrownBy(() -> authService.resetPassword(noTokenReq))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Reset code has expired");

        // Step 4: Reset password with wrong code
        PasswordResetToken validToken = PasswordResetToken.builder()
                .user(user)
                .codeHash("8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92") // SHA-256 of "123456"
                .expiresAt(Instant.now().plusSeconds(600))
                .used(false)
                .build();
        when(passwordResetTokenRepository.findTopByUser_EmailIgnoreCaseAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                .thenReturn(Optional.of(validToken));

        ResetPasswordRequest badReq = new ResetPasswordRequest("user@test.com", "999999", "NewPassword@123");
        assertThatThrownBy(() -> authService.resetPassword(badReq))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid reset code");

        // Step 5: Reset password with right code
        ResetPasswordRequest goodReq = new ResetPasswordRequest("user@test.com", "123456", "NewPassword@123");
        authService.resetPassword(goodReq);

        assertThat(validToken.isUsed()).isTrue();
        assertThat(passwordEncoder.matches("NewPassword@123", user.getPasswordHash())).isTrue();
    }

    @Test
    void forgotPassword_emailServiceConfigured_prodMode_dispatchesEmailAndHidesCode() {
        EmailService mockEmailService = mock(EmailService.class);
        when(mockEmailService.isConfigured()).thenReturn(true);
        when(mockEmailService.sendPasswordResetEmailAsync(anyString(), anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));

        AuthService prodAuthService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordResetTokenRepository,
                passwordEncoder,
                jwtService,
                mockEmailService,
                604800000L,
                "vault.test.com",
                false // exposeResetCode = false (production)
        );

        User user = User.builder().id(1L).email("prod@test.com").passwordHash("hash").displayName("Prod").build();
        when(userRepository.findByEmailIgnoreCase("prod@test.com")).thenReturn(Optional.of(user));

        Map<String, Object> resp = prodAuthService.forgotPassword(new ForgotPasswordRequest("prod@test.com"));

        assertThat(resp.get("email")).isEqualTo("prod@test.com");
        assertThat(resp.get("emailDelivered")).isEqualTo(true);
        assertThat(resp.get("resetCode")).isNull(); // NOT exposed in prod!
        assertThat(resp.get("message").toString()).contains("sent to your email inbox");

        verify(mockEmailService).sendPasswordResetEmailAsync(eq("prod@test.com"), anyString());
    }

    @Test
    void forgotPassword_emailServiceUnconfigured_prodMode_fallsBackToDirectCode() {
        EmailService unconfiguredEmailService = mock(EmailService.class);
        when(unconfiguredEmailService.isConfigured()).thenReturn(false);

        AuthService prodAuthService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordResetTokenRepository,
                passwordEncoder,
                jwtService,
                unconfiguredEmailService,
                604800000L,
                "vault.test.com",
                false // exposeResetCode = false
        );

        User user = User.builder().id(1L).email("unconfigured@test.com").passwordHash("hash").displayName("Unconfigured").build();
        when(userRepository.findByEmailIgnoreCase("unconfigured@test.com")).thenReturn(Optional.of(user));

        Map<String, Object> resp = prodAuthService.forgotPassword(new ForgotPasswordRequest("unconfigured@test.com"));

        assertThat(resp.get("email")).isEqualTo("unconfigured@test.com");
        assertThat(resp.get("emailDelivered")).isEqualTo(false);
        // Because email service was unconfigured, resetCode MUST be safely provided as fallback:
        assertThat(resp.get("resetCode")).isNotNull();
        assertThat(resp.get("resetCode").toString()).hasSize(6);
        assertThat(resp.get("message").toString()).contains("not configured");

        verify(unconfiguredEmailService, never()).sendPasswordResetEmailAsync(anyString(), anyString());
    }
}
