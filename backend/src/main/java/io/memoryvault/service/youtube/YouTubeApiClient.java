package io.memoryvault.service.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/** Thin direct-HTTP wrapper over the two YouTube Data API v3 calls this sync needs. */
@Component
public class YouTubeApiClient {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record PlaylistIds(String likedVideos, String watchLater) {
    }

    /** @return the authenticated user's "Liked videos" and "Watch later" playlist ids. */
    public PlaylistIds fetchPlaylistIds(String accessToken) throws Exception {
        JsonNode json = get("https://www.googleapis.com/youtube/v3/channels?part=contentDetails&mine=true", accessToken);
        JsonNode relatedPlaylists = json.path("items").path(0).path("contentDetails").path("relatedPlaylists");
        return new PlaylistIds(
                relatedPlaylists.path("likes").asText(null),
                relatedPlaylists.path("watchLater").asText(null)
        );
    }

    /** @return up to 50 most recent items in the given playlist. */
    public List<YouTubeVideo> fetchPlaylistItems(String accessToken, String playlistId) throws Exception {
        List<YouTubeVideo> videos = new ArrayList<>();
        if (playlistId == null) {
            return videos;
        }

        JsonNode json = get("https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&maxResults=50&playlistId=" + playlistId, accessToken);
        for (JsonNode item : json.path("items")) {
            JsonNode snippet = item.path("snippet");
            String videoId = snippet.path("resourceId").path("videoId").asText(null);
            if (videoId == null) {
                continue;
            }
            videos.add(new YouTubeVideo(
                    videoId,
                    snippet.path("title").asText(null),
                    snippet.path("description").asText(null),
                    snippet.path("thumbnails").path("high").path("url").asText(
                            snippet.path("thumbnails").path("default").path("url").asText(null)),
                    snippet.path("channelTitle").asText(null)
            ));
        }
        return videos;
    }

    private JsonNode get(String url, String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("YouTube API returned " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readTree(response.body());
    }
}
