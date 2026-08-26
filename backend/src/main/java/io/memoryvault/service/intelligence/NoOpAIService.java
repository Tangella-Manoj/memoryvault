package io.memoryvault.service.intelligence;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Test-profile stand-in for {@link AIService}: no network calls, always empty — so tests
 * exercise the same fallback paths (no summary/tags, no embedding) that a real deployment
 * hits when Ollama/Groq/HuggingFace are unreachable, without needing any of them running.
 */
@Service
@Profile("test")
public class NoOpAIService implements AIService {

    @Override
    public Optional<AISummaryResult> generateSummaryAndTags(String title, String description, String bodyText) {
        return Optional.empty();
    }

    @Override
    public Optional<List<Float>> generateEmbedding(String text) {
        return Optional.empty();
    }
}
