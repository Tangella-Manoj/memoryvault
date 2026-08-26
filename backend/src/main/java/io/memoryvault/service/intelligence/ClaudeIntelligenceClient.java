package io.memoryvault.service.intelligence;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Thin wrapper around the Anthropic Java SDK for the one thing the intelligence pipeline
 * needs from Claude: a 3-sentence summary plus a short tag list for a saved page. Retries
 * transient failures (rate limits, network errors) with exponential backoff; when no API
 * key is configured or every retry is exhausted, callers get {@link Optional#empty()} and
 * are expected to fall back to a non-AI summary (see {@code ContentIntelligenceService}).
 */
@Component
public class ClaudeIntelligenceClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeIntelligenceClient.class);
    private static final int MAX_ATTEMPTS = 3;

    private final AnthropicClient client;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean enabled;

    /**
     * @param apiKey the Anthropic API key from {@code app.anthropic.api-key}; when blank
     *               the client is disabled and {@link #summarizeAndTag} always returns empty
     * @param model  the Claude model id to call, from {@code app.anthropic.model}
     */
    public ClaudeIntelligenceClient(
            @Value("${app.anthropic.api-key}") String apiKey,
            @Value("${app.anthropic.model}") String model
    ) {
        this.model = model;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.client = enabled
                ? AnthropicOkHttpClient.builder().apiKey(apiKey).build()
                : null;
    }

    /**
     * Asks Claude for a 3-sentence summary and a handful of tags for a saved page, retrying
     * up to {@value #MAX_ATTEMPTS} times with exponential backoff on rate limits or transient
     * errors.
     *
     * @param title       the page's title, if known (may be null)
     * @param description the page's meta description, if known (may be null)
     * @param bodyText    extracted body text to ground the summary in (truncated internally)
     * @return the parsed summary and tags, or {@link Optional#empty()} if the client is
     *         disabled, every attempt failed, or the response couldn't be parsed as JSON
     */
    public Optional<ClaudeSummaryResult> summarizeAndTag(String title, String description, String bodyText) {
        if (!enabled) {
            return Optional.empty();
        }

        String prompt = """
                Given this saved web page, respond with ONLY a JSON object (no markdown fences, no prose) \
                in exactly this shape: {"summary": "<a 3 sentence summary>", "tags": ["tag1", "tag2", "tag3"]}

                Title: %s
                Description: %s
                Content: %s
                """.formatted(
                nullToEmpty(title),
                nullToEmpty(description),
                truncate(nullToEmpty(bodyText), 3000)
        );

        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1024L)
                .addUserMessage(prompt)
                .build();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Message response = client.messages().create(params);
                String text = response.content().stream()
                        .flatMap(block -> block.text().stream())
                        .map(t -> t.text())
                        .reduce("", String::concat);
                return parse(text);
            } catch (RateLimitException ex) {
                log.warn("Claude rate limited, attempt {}/{}", attempt, MAX_ATTEMPTS);
                backoff(attempt);
            } catch (Exception ex) {
                log.warn("Claude call failed, attempt {}/{}: {}", attempt, MAX_ATTEMPTS, ex.getMessage());
                backoff(attempt);
            }
        }
        return Optional.empty();
    }

    private Optional<ClaudeSummaryResult> parse(String text) {
        try {
            String cleaned = text.trim();
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start < 0 || end < 0 || end < start) {
                return Optional.empty();
            }
            JsonNode node = objectMapper.readTree(cleaned.substring(start, end + 1));
            String summary = node.path("summary").asText(null);
            List<String> tags = new ArrayList<>();
            node.path("tags").forEach(t -> tags.add(t.asText()));
            if (summary == null) {
                return Optional.empty();
            }
            return Optional.of(new ClaudeSummaryResult(summary, tags));
        } catch (Exception ex) {
            log.warn("Failed to parse Claude response as JSON: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(300L * (1L << attempt));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
