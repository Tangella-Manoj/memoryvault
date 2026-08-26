package io.memoryvault.service.youtube;

import io.memoryvault.domain.User;
import io.memoryvault.domain.UserIntegration;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ContentType;
import io.memoryvault.domain.enums.IntegrationPlatform;
import io.memoryvault.domain.enums.ItemSource;
import io.memoryvault.domain.enums.ItemStatus;
import io.memoryvault.repository.UserIntegrationRepository;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.repository.VaultItemRepository;
import io.memoryvault.service.TokenCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Syncs a user's YouTube liked videos and Watch Later playlist into their vault.
 * Deduplicates by {@code vault_items.external_id} (the YouTube video id) so re-running
 * a sync — manually or via the 6-hourly {@code @Scheduled} job — never creates duplicates.
 */
@Service
public class YouTubeSyncService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeSyncService.class);

    private final UserIntegrationRepository userIntegrationRepository;
    private final VaultItemRepository vaultItemRepository;
    private final UserRepository userRepository;
    private final TokenCipher tokenCipher;
    private final GoogleOAuthClient oAuthClient;
    private final YouTubeApiClient apiClient;

    public YouTubeSyncService(
            UserIntegrationRepository userIntegrationRepository,
            VaultItemRepository vaultItemRepository,
            UserRepository userRepository,
            TokenCipher tokenCipher,
            GoogleOAuthClient oAuthClient,
            YouTubeApiClient apiClient
    ) {
        this.userIntegrationRepository = userIntegrationRepository;
        this.vaultItemRepository = vaultItemRepository;
        this.userRepository = userRepository;
        this.tokenCipher = tokenCipher;
        this.oAuthClient = oAuthClient;
        this.apiClient = apiClient;
    }

    /**
     * Runs a full sync for one user: refreshes the access token if needed, fetches liked
     * videos + Watch Later, and creates any not-yet-seen videos as vault items.
     *
     * @param userId the user whose YouTube integration to sync
     * @return number of new vault items created
     * @throws IllegalStateException if the user has no YouTube integration connected
     */
    public int syncUser(Long userId) {
        UserIntegration integration = userIntegrationRepository.findByUserIdAndPlatform(userId, IntegrationPlatform.YOUTUBE)
                .orElseThrow(() -> new IllegalStateException("No YouTube integration connected for user " + userId));

        String accessToken = ensureFreshAccessToken(integration);

        try {
            YouTubeApiClient.PlaylistIds playlists = apiClient.fetchPlaylistIds(accessToken);
            List<YouTubeVideo> videos = new ArrayList<>();
            videos.addAll(apiClient.fetchPlaylistItems(accessToken, playlists.likedVideos()));
            videos.addAll(apiClient.fetchPlaylistItems(accessToken, playlists.watchLater()));

            int created = createMissingItems(userId, videos);

            markSynced(integration.getId());
            return created;
        } catch (Exception ex) {
            log.warn("YouTube sync failed for user {}: {}", userId, ex.getMessage());
            throw new IllegalStateException("YouTube sync failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Pure dedup-and-create step, deliberately separated from the network calls above so
     * it's testable with a plain list of videos — no HTTP mocking required.
     *
     * @param userId the owning user
     * @param videos videos fetched from YouTube (liked + Watch Later, may contain dupes
     *               between the two lists, and against already-synced items)
     * @return number of genuinely new vault items created
     */
    @Transactional
    public int createMissingItems(Long userId, List<YouTubeVideo> videos) {
        User user = userRepository.getReferenceById(userId);
        int created = 0;
        java.util.Set<String> seenThisRun = new java.util.HashSet<>();

        for (YouTubeVideo video : videos) {
            if (!seenThisRun.add(video.videoId())) {
                continue;
            }
            if (vaultItemRepository.existsByUserIdAndExternalId(userId, video.videoId())) {
                continue;
            }

            VaultItem item = VaultItem.builder()
                    .user(user)
                    .url(video.watchUrl())
                    .title(video.title())
                    .summary(video.description())
                    .ogImageUrl(video.thumbnailUrl())
                    .contentType(ContentType.VIDEO)
                    .status(ItemStatus.PROCESSED)
                    .source(ItemSource.YOUTUBE)
                    .externalId(video.videoId())
                    .importanceScore(java.math.BigDecimal.valueOf(0.6))
                    .build();
            vaultItemRepository.save(item);
            created++;
        }
        return created;
    }

    private String ensureFreshAccessToken(UserIntegration integration) {
        if (integration.getTokenExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return tokenCipher.decrypt(integration.getAccessToken());
        }

        try {
            String refreshToken = tokenCipher.decrypt(integration.getRefreshToken());
            GoogleOAuthClient.TokenResult refreshed = oAuthClient.refresh(refreshToken);

            integration.setAccessToken(tokenCipher.encrypt(refreshed.accessToken()));
            integration.setTokenExpiresAt(refreshed.expiresAt());
            userIntegrationRepository.save(integration);

            return refreshed.accessToken();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to refresh YouTube access token: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    void markSynced(Long integrationId) {
        userIntegrationRepository.findById(integrationId).ifPresent(i -> {
            i.setLastSyncedAt(Instant.now());
            userIntegrationRepository.save(i);
        });
    }
}
