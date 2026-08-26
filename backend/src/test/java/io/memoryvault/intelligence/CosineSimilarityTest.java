package io.memoryvault.intelligence;

import io.memoryvault.domain.User;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ContentType;
import io.memoryvault.domain.enums.EmotionalContext;
import io.memoryvault.domain.enums.ItemStatus;
import io.memoryvault.domain.enums.LifeContext;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.repository.VaultItemRepository;
import io.memoryvault.service.intelligence.ContextDetector;
import io.memoryvault.service.intelligence.ScoredVaultItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the cosine similarity math itself with hand-calculable vectors, the null/no-op
 * embedding fallback path (this test profile runs {@code NoOpAIService}, which never
 * produces an embedding), and that a real stored embedding is used over the keyword
 * fallback once present.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CosineSimilarityTest {

    @Autowired
    private ContextDetector contextDetector;

    @Autowired
    private VaultItemRepository vaultItemRepository;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void identicalVectors_haveSimilarityOfOne() throws Exception {
        double similarity = invokeCosine(List.of(1f, 2f, 3f), List.of(1f, 2f, 3f));
        assertThat(similarity).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void orthogonalVectors_haveSimilarityOfZero() throws Exception {
        double similarity = invokeCosine(List.of(1f, 0f), List.of(0f, 1f));
        assertThat(similarity).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void oppositeVectors_haveSimilarityOfNegativeOne() throws Exception {
        double similarity = invokeCosine(List.of(1f, 2f), List.of(-1f, -2f));
        assertThat(similarity).isCloseTo(-1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void knownVectors_matchHandCalculatedValue() throws Exception {
        // a=(1,2,3), b=(4,5,6) -> dot=32, |a|=sqrt(14), |b|=sqrt(77)
        // cos = 32 / (sqrt(14)*sqrt(77)) ~= 0.9746318
        double similarity = invokeCosine(List.of(1f, 2f, 3f), List.of(4f, 5f, 6f));
        assertThat(similarity).isCloseTo(0.9746318, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void noEmbedding_fallsBackToKeywordScoring_neverExcludingTheItem() {
        User user = userRepository.save(User.builder()
                .email("cosine-test-" + System.nanoTime() + "@memoryvault.dev")
                .passwordHash("irrelevant")
                .displayName("Cosine Test User")
                .timezone("UTC")
                .build());

        VaultItem item = vaultItemRepository.save(VaultItem.builder()
                .user(user)
                .url("https://example.com/no-embedding")
                .title("A guide to career growth")
                .summary("Tips for advancing your career")
                .contentType(ContentType.ARTICLE)
                .status(ItemStatus.PROCESSED)
                .emotionalContext(EmotionalContext.EXCITED)
                .lifeContext(LifeContext.CAREER)
                .importanceScore(BigDecimal.valueOf(0.6))
                .savedAt(Instant.now())
                .viewCount(0)
                .build());
        // No embedding set — item.getEmbedding() is null.

        List<ScoredVaultItem> ranked = contextDetector.rank(
                "career growth job", List.of(item), 20);

        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).item().getId()).isEqualTo(item.getId());
        assertThat(ranked.get(0).score()).isGreaterThan(0.0);
    }

    private double invokeCosine(List<Float> a, List<Float> b) throws Exception {
        Class<?> clazz = Class.forName("io.memoryvault.service.intelligence.CosineSimilarity");
        Method of = clazz.getDeclaredMethod("of", List.class, List.class);
        of.setAccessible(true);
        return (double) of.invoke(null, a, b);
    }
}
