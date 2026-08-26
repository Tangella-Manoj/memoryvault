package io.memoryvault.service.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * On startup, pulls the two models the dev AI stack depends on ({@code llama3.1:8b} for
 * generation, {@code nomic-embed-text} for embeddings) via Ollama's {@code /api/pull}
 * endpoint, so a fresh {@code docker compose up} needs no manual {@code ollama pull} step.
 * Best-effort: logs and continues on failure rather than blocking app startup — Ollama may
 * still be warming up, or the models may already be pulled from a previous run.
 */
@Component
@Profile("dev")
public class OllamaModelPullRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OllamaModelPullRunner.class);

    private final String baseUrl;
    private final String chatModel;
    private final String embeddingModel;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaModelPullRunner(
            @Value("${app.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${app.ai.ollama.chat-model:llama3.1:8b}") String chatModel,
            @Value("${app.ai.ollama.embedding-model:nomic-embed-text}") String embeddingModel
    ) {
        this.baseUrl = baseUrl;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void run(ApplicationArguments args) {
        pull(chatModel);
        pull(embeddingModel);
    }

    private void pull(String modelName) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("name", modelName, "stream", false));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/pull"))
                    .timeout(Duration.ofMinutes(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            log.info("Pulling Ollama model {} (this can take a while on first run)...", modelName);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("Ollama model {} ready", modelName);
            } else {
                log.warn("Ollama pull for {} returned status {}", modelName, response.statusCode());
            }
        } catch (Exception ex) {
            log.warn("Could not pull Ollama model {} on startup: {}. It can be pulled manually with " +
                    "`docker exec memoryvault_ollama ollama pull {}`.", modelName, ex.getMessage(), modelName);
        }
    }
}
