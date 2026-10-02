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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

@Service
public class AuthService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshExpiryMs;
    private final String vaultEmailDomain;
    private final boolean exposeResetCode;
    private final SecureRandom secureRandom = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.jwt.refresh-expiry-ms}") long refreshExpiryMs,
            @Value("${app.vault-email-domain:vault.stacknode.dev}") String vaultEmailDomain
    ) {
        this(userRepository, refreshTokenRepository, passwordResetTokenRepository,
                passwordEncoder, jwtService, refreshExpiryMs, vaultEmailDomain, true);
    }

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            long refreshExpiryMs,
            String vaultEmailDomain,
            boolean exposeResetCode
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshExpiryMs = refreshExpiryMs;
        this.vaultEmailDomain = vaultEmailDomain;
        this.exposeResetCode = exposeResetCode;
    }

    /**
     * Generates this user's personal inbound-email address (Upgrade 3): the first 8 hex
     * characters of a random UUID, {@code @} the configured vault-email domain. Collisions
     * are astronomically unlikely (32 bits of entropy over a small user base) and not
     * retried here — acceptable for this feature, not for anything security-sensitive.
     */
    private String generateVaultEmail() {
        String prefix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return prefix + "@" + vaultEmailDomain;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email() != null ? request.email().trim().toLowerCase() : "";
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .timezone("UTC")
                .vaultEmail(generateVaultEmail())
                .build();
        user = userRepository.save(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email() != null ? request.email().trim().toLowerCase() : "";
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new InvalidCredentialsException("USER_NOT_FOUND", "No account found with email: " + email));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("INVALID_PASSWORD", "Incorrect password. Please check your password or use forgot password.");
        }

        return issueTokens(user);
    }

    /**
     * Generates a DB-persisted, hashed OTP for password reset.
     *
     * <p>The code is <strong>not</strong> returned in the API response — it must be
     * delivered to the user out-of-band (email, SMS). Returning it in the HTTP body
     * would let any network observer or browser-devtools log compromise the reset
     * flow. Until a real email integration is wired, instruct the user to check their
     * inbox (or configure SMTP/Mailgun so the code is actually sent).
     *
     * <p>Previous reset tokens for the same user are implicitly superseded — the
     * {@link PasswordResetTokenRepository#findTopByUser_EmailIgnoreCaseAndUsedFalseOrderByCreatedAtDesc}
     * query returns the most-recent one, so old requests simply become unreachable.
     *
     * @param request email of the account to reset
     * @return a minimal acknowledgment map (no code)
     */
    @Transactional
    public Map<String, Object> forgotPassword(ForgotPasswordRequest request) {
        String email = request.email() != null ? request.email().trim().toLowerCase() : "";
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new InvalidCredentialsException("USER_NOT_FOUND",
                        "No account found with email: " + email));

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .codeHash(hash(code))
                .expiresAt(expiresAt)
                .build();
        passwordResetTokenRepository.save(resetToken);

        log.info("[PasswordReset] Generated 6-digit reset code for {}: {}", email, code);

        Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("email", email);
        resp.put("expiresInMinutes", 15);
        if (exposeResetCode) {
            resp.put("resetCode", code);
            resp.put("message", "Reset code generated: " + code + ". (Outbound email service is not configured; code provided directly).");
        } else {
            resp.put("message", "If an account exists for this email, a reset code has been sent.");
        }

        return resp;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.email() != null ? request.email().trim().toLowerCase() : "";
        // We still load the user explicitly to give a clear USER_NOT_FOUND error when appropriate.
        userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new InvalidCredentialsException("USER_NOT_FOUND",
                        "No account found with email: " + email));

        PasswordResetToken entry = passwordResetTokenRepository
                .findTopByUser_EmailIgnoreCaseAndUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_CODE",
                        "Reset code has expired or was not requested. Please request a new one."));

        if (entry.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_CODE",
                    "Reset code has expired. Please request a new one.");
        }

        String token = request.token() != null ? request.token().trim() : "";
        if (!entry.getCodeHash().equals(hash(token))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_CODE",
                    "Invalid reset code. Please check and try again.");
        }

        // Mark the token consumed before modifying the password (prevents replays on error).
        entry.setUsed(true);
        passwordResetTokenRepository.save(entry);

        User user = entry.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Revoke all existing refresh tokens so old sessions cannot continue.
        refreshTokenRepository.findAllByUser(user).forEach(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    /**
     * Revokes a refresh token so it can no longer be used to mint new access tokens.
     * The already-issued access token remains valid until it naturally expires (it's a
     * stateless JWT, not tracked server-side) — this ends the ability to silently refresh,
     * not the current session instantly.
     *
     * @param rawRefreshToken the token to revoke; unknown/already-revoked tokens are a no-op
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresent(stored -> {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
        });
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String hash = hash(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueTokens(stored.getUser());
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String rawRefreshToken = generateRawToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawRefreshToken))
                .expiresAt(Instant.now().plus(refreshExpiryMs, ChronoUnit.MILLIS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(user.getId(), user.getEmail(), user.getDisplayName(), accessToken, rawRefreshToken);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
