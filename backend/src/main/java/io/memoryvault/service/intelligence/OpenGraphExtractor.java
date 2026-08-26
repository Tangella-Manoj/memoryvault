package io.memoryvault.service.intelligence;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class OpenGraphExtractor {

    private static final int MAX_BODY_CHARS = 4000;

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
