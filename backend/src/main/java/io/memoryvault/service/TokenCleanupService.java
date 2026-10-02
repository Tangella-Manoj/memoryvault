package io.memoryvault.service;

import io.memoryvault.repository.PasswordResetTokenRepository;
import io.memoryvault.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Nightly cleanup of expired/revoked tokens from the database.
 * <p>
 * Refresh tokens are soft-revoked on logout and rotation but never auto-deleted —
 * without this job they accumulate indefinitely. Password reset tokens are
 * single-use and expire in 15 minutes — they also need periodic cleanup.
 * <p>
 * Runs at 03:00 UTC daily. The window is intentionally quiet to avoid any
 * contention with user activity. On Render free tier the service may be asleep
 * at this time; the job will run on the next wakeup after 03:00.
 */
@Service
public class TokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public TokenCleanupService(
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Scheduled(cron = "0 0 3 * * *")  // 03:00 UTC daily
    public void purgeExpiredTokens() {
        Instant now = Instant.now();
        log.info("[TokenCleanup] Starting nightly token purge at {}", now);

        try {
            refreshTokenRepository.deleteExpiredAndRevoked(now);
            log.info("[TokenCleanup] Refresh tokens purged");
        } catch (Exception e) {
            log.error("[TokenCleanup] Failed to purge refresh tokens", e);
        }

        try {
            passwordResetTokenRepository.deleteExpiredAndUsed(now);
            log.info("[TokenCleanup] Password reset tokens purged");
        } catch (Exception e) {
            log.error("[TokenCleanup] Failed to purge password reset tokens", e);
        }
    }
}
