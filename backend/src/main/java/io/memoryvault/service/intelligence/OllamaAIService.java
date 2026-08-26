package io.memoryvault.service.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dev implementation of {@link AIService}: talks to a local Ollama instance
 * (docker-compose service {@code ollama}, port 11434) — {@code llama3.1:8b} for
 * generation, {@code nomic-embed-text} for embeddings. Free, no API key, runs entirely
 * on the developer's machine.
 */
@Service
@Profile("dev")
public class OllamaAIService implements AIService {

    private static final Logger log = LoggerFactory.getLogger(OllamaAIService.class);

    private final ChatLanguageModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaAIService(
            @Value("${app.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${app.ai.ollama.chat-model:llama3.1:8b}") String chatModelName,
            @Value("${app.ai.ollama.embedding-model:nomic-embed-text}") String embeddingModelName
    ) {
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(chatModelName)
                .timeout(Duration.ofSeconds(60))
                .build();
        this.embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(embeddingModelName)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Override
    public Optional<AISummaryResult> generateSummaryAndTags(String title, String description, String bodyText) {
        String prompt = AIPromptBuilder.summaryPrompt(title, description, bodyText);
        try {
            String response = chatModel.generate(prompt);
            return parse(response);
        } catch (Exception ex) {
            log.warn("Ollama chat call failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<List<Float>> generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            Embedding embedding = embeddingModel.embed(text).content();
            List<Float> vector = new ArrayList<>();
            for (float f : embedding.vector()) {
                vector.add(f);
            }
            return Optional.of(vector);
        } catch (Exception ex) {
            log.warn("Ollama embedding call failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<AISummaryResult> parse(String text) {
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
            return Optional.of(new AISummaryResult(summary, tags.size() > 10 ? tags.subList(0, 10) : tags));
        } catch (Exception ex) {
            log.warn("Failed to parse Ollama response as JSON: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
