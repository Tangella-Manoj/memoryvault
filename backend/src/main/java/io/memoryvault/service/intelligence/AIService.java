package io.memoryvault.service.intelligence;

import java.util.List;
import java.util.Optional;

/**
 * The one seam between the intelligence pipeline and whichever free, open-source AI stack
 * is actually running behind it. {@link OllamaAIService} (dev, local Ollama) and
 * {@link GroqAIService} (prod, Groq + HuggingFace) are the only two implementations — the
 * rest of the codebase depends solely on this interface and never changes based on which
 * one is active.
 */
public interface AIService {

    /**
     * @param title       the page's title, if known (may be null)
     * @param description the page's meta description, if known (may be null)
     * @param bodyText    extracted body text to ground the summary in
     * @return a three-sentence summary and up to ten tags, or {@link Optional#empty()} if
     *         generation is unavailable or fails
     */
    Optional<AISummaryResult> generateSummaryAndTags(String title, String description, String bodyText);

    /**
     * @param text arbitrary text to embed
     * @return the embedding vector, or {@link Optional#empty()} if embedding is unavailable
     *         or fails
     */
    Optional<List<Float>> generateEmbedding(String text);
}
