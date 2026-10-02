package io.memoryvault.repository;

import io.memoryvault.domain.RefreshToken;
import io.memoryvault.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUser(User user);

    /**
     * Bulk-deletes all refresh tokens that have expired or been revoked.
     * Called nightly by {@link io.memoryvault.service.TokenCleanupService} to
     * prevent unbounded table growth (each login/refresh creates a new row).
     */
    @Transactional
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now or t.revoked = true")
    void deleteExpiredAndRevoked(@Param("now") Instant now);
}
