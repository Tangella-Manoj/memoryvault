package io.memoryvault.youtube;

import io.memoryvault.domain.User;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.repository.VaultItemRepository;
import io.memoryvault.service.youtube.YouTubeSyncService;
import io.memoryvault.service.youtube.YouTubeVideo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises YouTubeSyncService's dedup-and-create logic directly against real H2-backed
 * data — no HTTP mocking needed, since {@link YouTubeSyncService#createMissingItems} is
 * deliberately separated from the network calls that produce its input.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class YouTubeSyncServiceTest {

    @Autowired
    private YouTubeSyncService youTubeSyncService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VaultItemRepository vaultItemRepository;

    private Long userId;

    @BeforeEach
    void seedUser() {
        User user = userRepository.save(User.builder()
                .email("yt-test-" + System.nanoTime() + "@memoryvault.dev")
                .passwordHash("irrelevant")
                .displayName("YouTube Test User")
                .timezone("UTC")
                .build());
        userId = user.getId();
    }

    @Test
    void createsOneItemPerUniqueVideo() {
        List<YouTubeVideo> videos = List.of(
                new YouTubeVideo("vid1", "First video", "desc", "https://img/1.jpg", "Channel A"),
                new YouTubeVideo("vid2", "Second video", "desc", "https://img/2.jpg", "Channel B")
        );

        int created = youTubeSyncService.createMissingItems(userId, videos);

        assertThat(created).isEqualTo(2);
        assertThat(vaultItemRepository.countByUserId(userId)).isEqualTo(2);
    }

    @Test
    void dedupsAgainstAlreadySyncedVideos() {
        List<YouTubeVideo> videos = List.of(new YouTubeVideo("vid1", "First video", "desc", null, "Channel A"));
        youTubeSyncService.createMissingItems(userId, videos);

        // Re-run the exact same sync (as the 6-hourly scheduled job would).
        int createdOnRerun = youTubeSyncService.createMissingItems(userId, videos);

        assertThat(createdOnRerun).isZero();
        assertThat(vaultItemRepository.countByUserId(userId)).isEqualTo(1);
    }

    @Test
    void dedupsWithinTheSameBatch_whenLikedAndWatchLaterOverlap() {
        // A video that's both liked and in Watch Later — fetchPlaylistItems is called
        // once per playlist and the results concatenated, so this overlap is real input.
        List<YouTubeVideo> videos = List.of(
                new YouTubeVideo("vid1", "Liked and saved", "desc", null, "Channel A"),
                new YouTubeVideo("vid1", "Liked and saved", "desc", null, "Channel A")
        );

        int created = youTubeSyncService.createMissingItems(userId, videos);

        assertThat(created).isEqualTo(1);
        assertThat(vaultItemRepository.countByUserId(userId)).isEqualTo(1);
    }
}
