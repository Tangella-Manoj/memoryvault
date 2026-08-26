package io.memoryvault.domain;

import io.memoryvault.domain.enums.ResurfaceAction;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "resurface_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResurfaceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vault_item_id", nullable = false)
    private VaultItem vaultItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal score;

    @Column(name = "shown_at", nullable = false, updatable = false)
    private Instant shownAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ResurfaceAction action = ResurfaceAction.SHOWN;

    @PrePersist
    void onCreate() {
        if (shownAt == null) {
            shownAt = Instant.now();
        }
    }
}
