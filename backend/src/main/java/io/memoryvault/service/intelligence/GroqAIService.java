package io.memoryvault.service.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.huggingface.HuggingFaceEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
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
 * Prod implementation of {@link AIService}: Groq's free-tier-friendly OpenAI-compatible
 * API ({@code llama-3.1-8b-instant}) for generation, and HuggingFace's hosted inference
 * API for embeddings. Both are free/low-cost alternatives to the Claude API this
 * replaces — no Anthropic dependency anywhere in the deployed app.
 */
@Service
@Profile("prod")
public class GroqAIService implements AIService {

    private static final Logger log = LoggerFactory.getLogger(GroqAIService.class);

    private final ChatLanguageModel chatModel;
    private final HuggingFaceEmbeddingModel embeddingModel;
    private final boolean chatEnabled;
    private final boolean embeddingEnabled;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GroqAIService(
            @Value("${app.ai.groq.api-key:}") String groqApiKey,
            @Value("${app.ai.groq.base-url:https://api.groq.com/openai/v1}") String groqBaseUrl,
            @Value("${app.ai.groq.model:llama-3.1-8b-instant}") String groqModel,
            @Value("${app.ai.huggingface.api-key:}") String huggingFaceApiKey,
            @Value("${app.ai.huggingface.embedding-model:sentence-transformers/all-MiniLM-L6-v2}") String huggingFaceModel
    ) {
        this.chatEnabled = groqApiKey != null && !groqApiKey.isBlank();
        this.chatModel = chatEnabled
                ? OpenAiChatModel.builder()
                        .baseUrl(groqBaseUrl)
                        .apiKey(groqApiKey)
                        .modelName(groqModel)
                        .timeout(Duration.ofSeconds(60))
                        .build()
                : null;

        this.embeddingEnabled = huggingFaceApiKey != null && !huggingFaceApiKey.isBlank();
        this.embeddingModel = embeddingEnabled
                ? HuggingFaceEmbeddingModel.builder()
                        .accessToken(huggingFaceApiKey)
                        .modelId(huggingFaceModel)
                        .timeout(Duration.ofSeconds(60))
                        .build()
                : null;
    }

    @Override
    public Optional<AISummaryResult> generateSummaryAndTags(String title, String description, String bodyText) {
        if (!chatEnabled) {
            return Optional.empty();
        }
        String prompt = AIPromptBuilder.summaryPrompt(title, description, bodyText);
        try {
            String response = chatModel.generate(prompt);
            return parse(response);
        } catch (Exception ex) {
            log.warn("Groq chat call failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<List<Float>> generateEmbedding(String text) {
        if (!embeddingEnabled || text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            var embedding = embeddingModel.embed(text).content();
            List<Float> vector = new ArrayList<>();
            for (float f : embedding.vector()) {
                vector.add(f);
            }
            return Optional.of(vector);
        } catch (Exception ex) {
            log.warn("HuggingFace embedding call failed: {}", ex.getMessage());
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
            log.warn("Failed to parse Groq response as JSON: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
