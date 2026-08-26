package io.memoryvault.service.youtube;

import io.memoryvault.domain.UserIntegration;
import io.memoryvault.domain.enums.IntegrationPlatform;
import io.memoryvault.repository.UserIntegrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs {@link YouTubeSyncService} for every user with sync enabled, every 6 hours. */
@Component
public class YouTubeScheduledSyncJob {

    private static final Logger log = LoggerFactory.getLogger(YouTubeScheduledSyncJob.class);

    private final UserIntegrationRepository userIntegrationRepository;
    private final YouTubeSyncService youTubeSyncService;

    public YouTubeScheduledSyncJob(UserIntegrationRepository userIntegrationRepository, YouTubeSyncService youTubeSyncService) {
        this.userIntegrationRepository = userIntegrationRepository;
        this.youTubeSyncService = youTubeSyncService;
    }

    @Scheduled(fixedRate = 6, timeUnit = java.util.concurrent.TimeUnit.HOURS)
    @Async("intelligenceExecutor")
    public void runForAllUsers() {
        var integrations = userIntegrationRepository.findByPlatformAndSyncEnabledTrue(IntegrationPlatform.YOUTUBE);
        log.info("YouTube scheduled sync: running for {} connected accounts", integrations.size());

        for (UserIntegration integration : integrations) {
            try {
                youTubeSyncService.syncUser(integration.getUser().getId());
            } catch (Exception ex) {
                log.warn("Scheduled YouTube sync failed for user {}: {}", integration.getUser().getId(), ex.getMessage());
            }
        }
    }
}
