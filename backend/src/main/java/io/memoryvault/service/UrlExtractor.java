package io.memoryvault.service;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the single best URL out of a PWA share-target payload's three loosely-structured
 * fields — Android's share intent can put the shared link in any of {@code url}, in the
 * middle of {@code text} (common for apps like WhatsApp/Twitter), or occasionally folded
 * into {@code title}.
 */
@Component
public class UrlExtractor {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s]+");

    /**
     * @param title the shared title field, checked last
     * @param text  the shared text field, checked second (most apps put the link here)
     * @param url   the shared url field, checked first (some apps populate it directly)
     * @return the best-guess URL, or {@code null} if none of the three fields contain one
     */
    public String extractBestUrl(String title, String text, String url) {
        if (isUrl(url)) {
            return url.trim();
        }
        String fromText = firstUrlIn(text);
        if (fromText != null) {
            return fromText;
        }
        return firstUrlIn(title);
    }

    private boolean isUrl(String candidate) {
        return candidate != null && candidate.trim().matches("^https?://.+");
    }

    private String firstUrlIn(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = URL_PATTERN.matcher(value);
        return matcher.find() ? matcher.group() : null;
    }
}
