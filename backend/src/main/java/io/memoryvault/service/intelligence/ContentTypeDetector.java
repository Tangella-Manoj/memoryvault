package io.memoryvault.service.intelligence;

import io.memoryvault.domain.enums.ContentType;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Classifies a saved URL into a {@link ContentType} using host-name patterns (YouTube,
 * GitHub, Twitter/X, common storefronts) first, then falling back to simple heuristics
 * over the extracted page metadata (a lone image with no body text reads as {@code IMAGE},
 * a long body reads as {@code ARTICLE}) when the host doesn't identify the type.
 */
@Component
public class ContentTypeDetector {

    /**
     * @param url      the saved URL, used for host-based pattern matching
     * @param metadata previously extracted page metadata, used when the host alone
     *                 isn't conclusive
     * @return the best-guess {@link ContentType}; {@code OTHER} when nothing matches
     */
    public ContentType detect(String url, OpenGraphMetadata metadata) {
        String host = hostOf(url);

        if (host.contains("youtube.com") || host.contains("youtu.be") || host.contains("vimeo.com")) {
            return ContentType.VIDEO;
        }
        if (host.contains("twitter.com") || host.contains("x.com")) {
            return url.contains("/status/") ? ContentType.TWEET : ContentType.THREAD;
        }
        if (host.contains("reddit.com")) {
            return ContentType.THREAD;
        }
        if (host.contains("medium.com") || host.contains("substack.com") || host.contains("dev.to")) {
            return ContentType.ARTICLE;
        }
        if (host.contains("instagram.com")) {
            return (url.contains("/reel/") || url.contains("/tv/")) ? ContentType.VIDEO : ContentType.IMAGE;
        }
        if (host.contains("github.com") || host.contains("gitlab.com")) {
            return ContentType.REPO;
        }
        if (host.contains("amazon.") || host.contains("etsy.com") || host.contains("shopify")) {
            return ContentType.PRODUCT;
        }
        if (host.contains("docs.google.com") || url.endsWith(".pdf")) {
            return ContentType.DOCUMENT;
        }
        if (metadata.imageUrl() != null && metadata.bodyText().isBlank()) {
            return ContentType.IMAGE;
        }
        if (metadata.bodyText() != null && metadata.bodyText().length() > 500) {
            return ContentType.ARTICLE;
        }
        return ContentType.OTHER;
    }

    private String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host.toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }
}
