package io.memoryvault.service;

import io.memoryvault.domain.RefreshToken;
import io.memoryvault.domain.User;
import io.memoryvault.dto.auth.AuthResponse;
import io.memoryvault.dto.auth.LoginRequest;
import io.memoryvault.dto.auth.RegisterRequest;
import io.memoryvault.exception.EmailAlreadyRegisteredException;
import io.memoryvault.exception.InvalidCredentialsException;
import io.memoryvault.exception.InvalidRefreshTokenException;
import io.memoryvault.repository.RefreshTokenRepository;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
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

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshExpiryMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.jwt.refresh-expiry-ms}") long refreshExpiryMs
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshExpiryMs = refreshExpiryMs;
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
                .build();
        user = userRepository.save(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return issueTokens(user);
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
