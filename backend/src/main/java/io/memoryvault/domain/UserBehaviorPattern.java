package io.memoryvault.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "user_behavior_patterns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBehaviorPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "pattern_type", nullable = false, length = 60)
    private String patternType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pattern_value", nullable = false, columnDefinition = "json")
    private String patternValue;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @PrePersist
    void onCreate() {
        if (computedAt == null) {
            computedAt = Instant.now();
        }
    }
}
