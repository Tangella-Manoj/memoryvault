package io.memoryvault.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "user_notification_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "optimal_hour", nullable = false)
    @Builder.Default
    private Integer optimalHour = 8;

    @Column(name = "optimal_day_of_week")
    private Integer optimalDayOfWeek;

    @Column(name = "push_subscription_json", columnDefinition = "TEXT")
    private String pushSubscriptionJson;

    @Column(name = "notifications_enabled", nullable = false)
    @Builder.Default
    private boolean notificationsEnabled = false;

    @Column(name = "last_calculated_at")
    private Instant lastCalculatedAt;
}
