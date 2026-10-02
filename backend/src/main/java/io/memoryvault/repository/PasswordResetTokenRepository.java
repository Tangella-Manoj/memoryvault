package io.memoryvault.repository;

import io.memoryvault.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Finds the most recently created, still-unused token for the given email.
     * Used during {@code /auth/reset-password} to validate the submitted code.
     */
    Optional<PasswordResetToken> findTopByUser_EmailIgnoreCaseAndUsedFalseOrderByCreatedAtDesc(
            String email);

    /**
     * Bulk-deletes all tokens that have either expired or been used.
     * Called nightly by {@link io.memoryvault.service.TokenCleanupService}.
     */
    @Transactional
    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :now or t.used = true")
    void deleteExpiredAndUsed(@Param("now") Instant now);
}
