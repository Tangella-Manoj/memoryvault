package io.memoryvault.service.youtube;

/**
 * One video pulled from a YouTube playlist (liked videos or Watch Later), before it
 * becomes a {@link io.memoryvault.domain.VaultItem}.
 */
public record YouTubeVideo(
        String videoId,
        String title,
        String description,
        String thumbnailUrl,
        String channelTitle
) {
    public String watchUrl() {
        return "https://www.youtube.com/watch?v=" + videoId;
    }
}
