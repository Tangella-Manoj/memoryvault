package io.memoryvault.service.intelligence;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * Fetches a saved URL and extracts Open Graph metadata (title, description, image) with
 * plain-HTML fallbacks (page {@code <title>}, the standard {@code meta[name=description]})
 * for pages that don't publish OG tags, plus a size-capped plain-text body used downstream
 * for content-type detection and the AI summary prompt.
 */
@Component
public class OpenGraphExtractor {

    private static final int MAX_BODY_CHARS = 4000;

    private static final java.util.regex.Pattern YT_PATTERN =
            java.util.regex.Pattern.compile("(?:v=|youtu\\.be/|shorts/|embed/)([a-zA-Z0-9_-]{11})");

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public OpenGraphExtractor() {
        this(new com.fasterxml.jackson.databind.ObjectMapper());
    }

    public OpenGraphExtractor(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new com.fasterxml.jackson.databind.ObjectMapper();
    }

    /**
     * @param url the URL to fetch, with an 8s connect/read timeout
     * @return extracted title, description, image URL, and truncated body text — any
     *         field the page doesn't provide is {@code null} (body text is {@code ""} instead)
     * @throws java.io.IOException if the page can't be fetched (network error, timeout, non-2xx)
     */
    public OpenGraphMetadata extract(String url) throws java.io.IOException {
        // Fast-path for YouTube: OEmbed provides clean title, channel author, and high-res thumbnail with 0 auth
        java.util.regex.Matcher ytMatcher = YT_PATTERN.matcher(url);
        if (ytMatcher.find()) {
            String videoId = ytMatcher.group(1);
            String fallbackThumb = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
            try {
                String oembedUrl = "https://www.youtube.com/oembed?url=" +
                        java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8) + "&format=json";
                String json = Jsoup.connect(oembedUrl)
                        .userAgent("Mozilla/5.0 (compatible; MemoryVaultBot/1.0)")
                        .timeout(4000)
                        .ignoreContentType(true)
                        .execute()
                        .body();
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
                String title = root.path("title").asText(null);
                String author = root.path("author_name").asText(null);
                String thumb = root.path("thumbnail_url").asText(fallbackThumb);
                String desc = (author != null && !author.isBlank()) ? "YouTube video by " + author : "YouTube video";
                String body = desc + (title != null ? ". " + title : "");
                return new OpenGraphMetadata(
                        blankToNull(title),
                        blankToNull(desc),
                        blankToNull(thumb != null ? thumb : fallbackThumb),
                        body
                );
            } catch (Exception ignored) {
                // If oembed fails (e.g. rate limit), fall back to standard HTML scraping
            }
        }

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; MemoryVaultBot/1.0)")
                .timeout(8000)
                .get();

        String title = firstNonBlank(
                doc.select("meta[property=og:title]").attr("content"),
                doc.title()
        );
        String description = firstNonBlank(
                doc.select("meta[property=og:description]").attr("content"),
                doc.select("meta[name=description]").attr("content")
        );
        String imageUrl = doc.select("meta[property=og:image]").attr("content");
        if ((imageUrl == null || imageUrl.isBlank()) && ytMatcher.reset().find()) {
            imageUrl = "https://i.ytimg.com/vi/" + ytMatcher.group(1) + "/hqdefault.jpg";
        }
        String bodyText = doc.body() != null ? doc.body().text() : "";
        if (bodyText.length() > MAX_BODY_CHARS) {
            bodyText = bodyText.substring(0, MAX_BODY_CHARS);
        }

        return new OpenGraphMetadata(
                blankToNull(title),
                blankToNull(description),
                blankToNull(imageUrl),
                bodyText
        );
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
