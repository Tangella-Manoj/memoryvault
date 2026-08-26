package io.memoryvault.service;

import io.memoryvault.domain.UserNotificationPreference;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ItemStatus;
import io.memoryvault.repository.UserNotificationPreferenceRepository;
import io.memoryvault.repository.VaultItemRepository;
import io.memoryvault.service.intelligence.ResurfaceEngine;
import io.memoryvault.service.intelligence.ScoredVaultItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Hourly job: for every user whose learned {@code optimal_hour} matches the current UTC
 * hour, sends a push notification surfacing their top 3 resurfacing candidates right now.
 */
@Service
public class NotificationSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationSchedulerService.class);

    private final UserNotificationPreferenceRepository preferenceRepository;
    private final VaultItemRepository vaultItemRepository;
    private final ResurfaceEngine resurfaceEngine;
    private final PushNotificationSender pushNotificationSender;

    public NotificationSchedulerService(
            UserNotificationPreferenceRepository preferenceRepository,
            VaultItemRepository vaultItemRepository,
            ResurfaceEngine resurfaceEngine,
            PushNotificationSender pushNotificationSender
    ) {
        this.preferenceRepository = preferenceRepository;
        this.vaultItemRepository = vaultItemRepository;
        this.resurfaceEngine = resurfaceEngine;
        this.pushNotificationSender = pushNotificationSender;
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    public void runHourly() {
        int currentHour = Instant.now().atZone(ZoneOffset.UTC).getHour();
        run(currentHour);
    }

    /**
     * @param currentHour the UTC hour to match users' optimal_hour against — pulled out
     *                     of {@link #runHourly} so a manual test trigger can pass any hour
     * @return number of notifications successfully sent
     */
    public int run(int currentHour) {
        List<UserNotificationPreference> due = preferenceRepository.findByNotificationsEnabledTrueAndOptimalHour(currentHour);
        log.info("NotificationScheduler: {} user(s) due at hour {}", due.size(), currentHour);

        int sent = 0;
        for (UserNotificationPreference preference : due) {
            if (preference.getPushSubscriptionJson() == null) {
                continue;
            }
            if (sendForUser(preference)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean sendForUser(UserNotificationPreference preference) {
        Long userId = preference.getUser().getId();
        List<VaultItem> candidates = vaultItemRepository.findByUserIdOrderBySavedAtDesc(userId, Pageable.unpaged())
                .getContent()
                .stream()
                .filter(v -> v.getStatus() == ItemStatus.PROCESSED)
                .collect(Collectors.toList());

        List<ScoredVaultItem> top = resurfaceEngine.topResurfaceCandidates("", candidates, candidates.size(), 3);
        if (top.isEmpty()) {
            return false;
        }

        VaultItem topItem = top.get(0).item();
        String title = "3 things you saved and forgot";
        String body = topItem.getTitle() != null ? topItem.getTitle() : topItem.getUrl();

        return pushNotificationSender.send(preference.getPushSubscriptionJson(), title, body, topItem.getId());
    }
}
