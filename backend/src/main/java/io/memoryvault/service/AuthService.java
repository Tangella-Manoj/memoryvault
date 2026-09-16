package io.memoryvault.service;

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
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private record ResetCodeEntry(String email, String code, Instant expiresAt) {}

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshExpiryMs;
    private final String vaultEmailDomain;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, ResetCodeEntry> resetCodes = new ConcurrentHashMap<>();

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.jwt.refresh-expiry-ms}") long refreshExpiryMs,
            @Value("${app.vault-email-domain:vault.stacknode.dev}") String vaultEmailDomain
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshExpiryMs = refreshExpiryMs;
        this.vaultEmailDomain = vaultEmailDomain;
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
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException(request.email());
        }

        User user = User.builder()
                .email(request.email())
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
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("No account found with email: " + email));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Incorrect password. Please check your password or use forgot password.");
        }

        return issueTokens(user);
    }

    public Map<String, Object> forgotPassword(ForgotPasswordRequest request) {
        String email = request.email() != null ? request.email().trim().toLowerCase() : "";
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("No account found with email: " + email));

        // Generate a 6-digit numeric reset code
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        resetCodes.put(email, new ResetCodeEntry(email, code, expiresAt));

        return Map.of(
                "email", email,
                "resetCode", code,
                "expiresInMinutes", 15,
                "message", "Password reset code generated. Use code " + code + " to set your new password."
        );
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.email() != null ? request.email().trim().toLowerCase() : "";
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("No account found with email: " + email));

        ResetCodeEntry entry = resetCodes.get(email);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_CODE",
                    "Reset code has expired or was not requested. Please request a new one.");
        }

        if (!entry.code().equals(request.token().trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_CODE",
                    "Invalid reset code. Please check and try again.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Consume reset code
        resetCodes.remove(email);

        // Revoke all existing refresh tokens
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
