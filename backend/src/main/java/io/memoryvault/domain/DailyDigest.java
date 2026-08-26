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
import java.time.LocalDate;

@Entity
@Table(name = "daily_digests", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "digest_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyDigest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "digest_date", nullable = false)
    private LocalDate digestDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "item_ids", nullable = false, columnDefinition = "json")
    private String itemIds;

    @Column(name = "sent_at")
    private Instant sentAt;
}
