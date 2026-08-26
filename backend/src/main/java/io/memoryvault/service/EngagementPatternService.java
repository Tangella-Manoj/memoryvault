package io.memoryvault.service;

import io.memoryvault.domain.ResurfaceEvent;
import io.memoryvault.domain.User;
import io.memoryvault.domain.UserNotificationPreference;
import io.memoryvault.domain.enums.ResurfaceAction;
import io.memoryvault.repository.ResurfaceEventRepository;
import io.memoryvault.repository.UserNotificationPreferenceRepository;
import io.memoryvault.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Learns each user's "optimal hour" for resurfacing notifications: the hour of day (UTC)
 * where they've historically actually engaged with (not dismissed) a resurfaced item.
 */
@Service
public class EngagementPatternService {

    private static final Logger log = LoggerFactory.getLogger(EngagementPatternService.class);
    private static final int MIN_EVENTS_FOR_LEARNING = 10;
    private static final int DEFAULT_HOUR = 8;
    private static final double RECENCY_DECAY_LAMBDA = 0.03; // slower decay than resurfacing itself — patterns should be stable

    private final UserRepository userRepository;
    private final ResurfaceEventRepository resurfaceEventRepository;
    private final UserNotificationPreferenceRepository preferenceRepository;

    public EngagementPatternService(
            UserRepository userRepository,
            ResurfaceEventRepository resurfaceEventRepository,
            UserNotificationPreferenceRepository preferenceRepository
    ) {
        this.userRepository = userRepository;
        this.resurfaceEventRepository = resurfaceEventRepository;
        this.preferenceRepository = preferenceRepository;
    }

    @Scheduled(fixedRate = 48, timeUnit = TimeUnit.HOURS)
    public void recalculateForAllUsers() {
        List<User> users = userRepository.findAll();
        log.info("EngagementPatternService: recalculating optimal hour for {} users", users.size());
        for (User user : users) {
            recalculateForUser(user.getId());
        }
    }

    /**
     * @param userId the user to recompute the optimal notification hour for
     * @return the computed (or default) optimal hour, 0-23
     */
    @Transactional
    public int recalculateForUser(Long userId) {
        List<ResurfaceEvent> engaged = resurfaceEventRepository.findByUserIdAndActionIn(
                userId, List.of(ResurfaceAction.VIEWED, ResurfaceAction.SAVED_AGAIN));

        int optimalHour = engaged.size() < MIN_EVENTS_FOR_LEARNING
                ? DEFAULT_HOUR
                : computeWeightedOptimalHour(engaged);

        User userRef = userRepository.getReferenceById(userId);
        UserNotificationPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> UserNotificationPreference.builder().user(userRef).build());
        preference.setOptimalHour(optimalHour);
        preference.setLastCalculatedAt(Instant.now());
        preferenceRepository.save(preference);

        return optimalHour;
    }

    private int computeWeightedOptimalHour(List<ResurfaceEvent> events) {
        double[] hourScores = new double[24];
        Instant now = Instant.now();

        for (ResurfaceEvent event : events) {
            int hour = event.getShownAt().atZone(ZoneOffset.UTC).getHour();
            long daysAgo = Duration.between(event.getShownAt(), now).toDays();
            double weight = Math.exp(-RECENCY_DECAY_LAMBDA * daysAgo);
            hourScores[hour] += weight;
        }

        int bestHour = DEFAULT_HOUR;
        double bestScore = -1;
        for (int hour = 0; hour < 24; hour++) {
            if (hourScores[hour] > bestScore) {
                bestScore = hourScores[hour];
                bestHour = hour;
            }
        }
        return bestHour;
    }
}
