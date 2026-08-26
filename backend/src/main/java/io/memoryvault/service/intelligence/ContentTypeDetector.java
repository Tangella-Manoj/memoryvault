package io.memoryvault.service.intelligence;

import io.memoryvault.domain.enums.ContentType;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class ContentTypeDetector {

    public ContentType detect(String url, OpenGraphMetadata metadata) {
        String host = hostOf(url);

        if (host.contains("youtube.com") || host.contains("youtu.be") || host.contains("vimeo.com")) {
            return ContentType.VIDEO;
        }
        if (host.contains("twitter.com") || host.contains("x.com")) {
            return url.contains("/status/") ? ContentType.TWEET : ContentType.THREAD;
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
