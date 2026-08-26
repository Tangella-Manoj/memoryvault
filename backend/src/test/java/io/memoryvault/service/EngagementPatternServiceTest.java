package io.memoryvault.service;

import io.memoryvault.domain.ResurfaceEvent;
import io.memoryvault.domain.User;
import io.memoryvault.domain.UserNotificationPreference;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ContentType;
import io.memoryvault.domain.enums.EmotionalContext;
import io.memoryvault.domain.enums.ItemStatus;
import io.memoryvault.domain.enums.LifeContext;
import io.memoryvault.domain.enums.ResurfaceAction;
import io.memoryvault.repository.ResurfaceEventRepository;
import io.memoryvault.repository.UserNotificationPreferenceRepository;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.repository.VaultItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EngagementPatternServiceTest {

    @Autowired
    private EngagementPatternService engagementPatternService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VaultItemRepository vaultItemRepository;

    @Autowired
    private ResurfaceEventRepository resurfaceEventRepository;

    @Autowired
    private UserNotificationPreferenceRepository preferenceRepository;

    private User newUser() {
        return userRepository.save(User.builder()
                .email("engagement-test-" + System.nanoTime() + "@memoryvault.dev")
                .passwordHash("irrelevant")
                .displayName("Engagement Test User")
                .timezone("UTC")
                .build());
    }

    @Test
    void fewerThanTenEvents_defaultsToEightAm() {
        User user = newUser();
        VaultItem item = seedItem(user);
        // Only 3 qualifying events — below the 10-event learning threshold.
        for (int i = 0; i < 3; i++) {
            saveEvent(user, item, ResurfaceAction.VIEWED, 14);
        }

        int hour = engagementPatternService.recalculateForUser(user.getId());

        assertThat(hour).isEqualTo(8);
    }

    @Test
    void tenOrMoreEvents_picksTheHourWithMostEngagement() {
        User user = newUser();
        VaultItem item = seedItem(user);

        // 8 engagements at hour 20, 2 at hour 9 — hour 20 should win clearly.
        for (int i = 0; i < 8; i++) {
            saveEvent(user, item, ResurfaceAction.VIEWED, 20);
        }
        for (int i = 0; i < 2; i++) {
            saveEvent(user, item, ResurfaceAction.SAVED_AGAIN, 9);
        }

        int hour = engagementPatternService.recalculateForUser(user.getId());

        assertThat(hour).isEqualTo(20);

        UserNotificationPreference saved = preferenceRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(saved.getOptimalHour()).isEqualTo(20);
        assertThat(saved.getLastCalculatedAt()).isNotNull();
    }

    @Test
    void dismissedEvents_areNotCountedAsEngagement() {
        User user = newUser();
        VaultItem item = seedItem(user);

        // 12 DISMISSED events shouldn't count toward the learning threshold at all.
        for (int i = 0; i < 12; i++) {
            saveEvent(user, item, ResurfaceAction.DISMISSED, 15);
        }

        int hour = engagementPatternService.recalculateForUser(user.getId());

        assertThat(hour).isEqualTo(8); // falls back to default since 0 qualifying events
    }

    private VaultItem seedItem(User user) {
        return vaultItemRepository.save(VaultItem.builder()
                .user(user)
                .url("https://example.com/engagement-test")
                .status(ItemStatus.PROCESSED)
                .contentType(ContentType.ARTICLE)
                .emotionalContext(EmotionalContext.NEUTRAL)
                .lifeContext(LifeContext.OTHER)
                .importanceScore(BigDecimal.valueOf(0.5))
                .savedAt(Instant.now())
                .build());
    }

    private void saveEvent(User user, VaultItem item, ResurfaceAction action, int hourOfDay) {
        Instant shownAt = Instant.now().truncatedTo(ChronoUnit.DAYS).atZone(ZoneOffset.UTC)
                .withHour(hourOfDay).toInstant();
        resurfaceEventRepository.save(ResurfaceEvent.builder()
                .user(user)
                .vaultItem(item)
                .reason("test")
                .score(BigDecimal.valueOf(0.5))
                .shownAt(shownAt)
                .action(action)
                .build());
    }
}
