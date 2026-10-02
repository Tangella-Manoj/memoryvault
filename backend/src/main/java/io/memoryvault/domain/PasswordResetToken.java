package io.memoryvault.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Persisted password reset OTP.
 *
 * <p>Stores a SHA-256 hash of the 6-digit code (never the plaintext). The
 * {@link #used} flag and {@link #expiresAt} column allow safe validation and
 * are the basis for the nightly cleanup job in
 * {@link io.memoryvault.service.TokenCleanupService}.
 *
 * <p>Replaces the former in-memory {@code ConcurrentHashMap} in
 * {@link io.memoryvault.service.AuthService}, which was lost on every Render
 * cold-start and could not be horizontally scaled.
 */
@Entity
@Table(name = "password_reset_tokens",
       indexes = @Index(name = "idx_prt_user_used", columnList = "user_id, used"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 hex digest of the 6-digit OTP. Never store the plain code. */
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** True once the code has been successfully used to reset a password. */
    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
