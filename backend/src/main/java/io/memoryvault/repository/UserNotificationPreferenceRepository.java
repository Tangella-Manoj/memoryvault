package io.memoryvault.repository;

import io.memoryvault.domain.UserNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserNotificationPreferenceRepository extends JpaRepository<UserNotificationPreference, Long> {

    Optional<UserNotificationPreference> findByUserId(Long userId);

    List<UserNotificationPreference> findByNotificationsEnabledTrueAndOptimalHour(Integer optimalHour);
}
