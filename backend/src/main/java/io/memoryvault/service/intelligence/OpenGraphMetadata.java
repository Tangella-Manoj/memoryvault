package io.memoryvault.service.intelligence;

/**
 * Page metadata extracted by {@link OpenGraphExtractor} for one URL.
 *
 * @param title       the page's Open Graph or plain {@code <title>}, or null if neither is present
 * @param description the page's Open Graph or meta description, or null if neither is present
 * @param imageUrl    the page's Open Graph image URL, or null if not present
 * @param bodyText    plain-text page body, truncated to a fixed length; empty string if unavailable
 */
public record OpenGraphMetadata(
        String title,
        String description,
        String imageUrl,
        String bodyText
) {
}
