package io.memoryvault.service.intelligence;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * Fetches a saved URL and extracts Open Graph metadata (title, description, image) with
 * plain-HTML fallbacks (page {@code <title>}, the standard {@code meta[name=description]})
 * for pages that don't publish OG tags, plus a size-capped plain-text body used downstream
 * for content-type detection and the Claude summary prompt.
 */
@Component
public class OpenGraphExtractor {

    private static final int MAX_BODY_CHARS = 4000;

    /**
     * @param url the URL to fetch, with an 8s connect/read timeout
     * @return extracted title, description, image URL, and truncated body text — any
     *         field the page doesn't provide is {@code null} (body text is {@code ""} instead)
     * @throws java.io.IOException if the page can't be fetched (network error, timeout, non-2xx)
     */
    public OpenGraphMetadata extract(String url) throws java.io.IOException {
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
