package io.memoryvault.domain;

import io.memoryvault.domain.enums.ContentType;
import io.memoryvault.domain.enums.EmotionalContext;
import io.memoryvault.domain.enums.ItemSource;
import io.memoryvault.domain.enums.ItemStatus;
import io.memoryvault.domain.enums.LifeContext;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "vault_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VaultItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    /** JSON array of floats — the semantic embedding of title+summary+tags. Null until enriched. */
    @Column(columnDefinition = "TEXT")
    private String embedding;

    @Column(name = "og_image_url", length = 2048)
    private String ogImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    @Builder.Default
    private ContentType contentType = ContentType.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ItemStatus status = ItemStatus.PROCESSING;

    @Enumerated(EnumType.STRING)
    @Column(name = "emotional_context", nullable = false)
    @Builder.Default
    private EmotionalContext emotionalContext = EmotionalContext.NEUTRAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "life_context", nullable = false)
    @Builder.Default
    private LifeContext lifeContext = LifeContext.OTHER;

    @Column(name = "importance_score", nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal importanceScore = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ItemSource source = ItemSource.WEB;

    /** External platform id (e.g. YouTube video id) used for cross-source dedup. Null for manual saves. */
    @Column(name = "external_id")
    private String externalId;

    @Column(name = "saved_at", nullable = false, updatable = false)
    private Instant savedAt;

    @Column(name = "last_surfaced_at")
    private Instant lastSurfacedAt;

    @Column(name = "last_viewed_at")
    private Instant lastViewedAt;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    @ManyToMany
    @JoinTable(
            name = "vault_item_tags",
            joinColumns = @JoinColumn(name = "vault_item_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

    @PrePersist
    void onCreate() {
        if (savedAt == null) {
            savedAt = Instant.now();
        }
    }
}
