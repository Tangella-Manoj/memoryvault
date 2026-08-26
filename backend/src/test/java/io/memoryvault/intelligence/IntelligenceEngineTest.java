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
import io.memoryvault.service.intelligence.ResurfaceEngine;
import io.memoryvault.service.intelligence.ScoredVaultItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seeds exactly 20 real, H2-backed vault items with known context values and verifies
 * ContextDetector's ranking and ResurfaceEngine's decay/forgotten-bonus scoring against
 * that seeded ground truth, plus the 30-day forgotten-gems threshold.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IntelligenceEngineTest {

    @Autowired
    private ContextDetector contextDetector;

    @Autowired
    private ResurfaceEngine resurfaceEngine;

    @Autowired
    private VaultItemRepository vaultItemRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    // group -> (title, summary, lifeContext, emotionalContext)
    private record GroupSpec(String title, String summary, LifeContext life, EmotionalContext emotion) {
    }

    private static final List<GroupSpec> GROUPS = List.of(
            new GroupSpec("New job offer at a startup", "Excited about the new role and career growth", LifeContext.CAREER, EmotionalContext.EXCITED),
            new GroupSpec("Meditation guide for beginners", "A calm mindfulness practice for daily health", LifeContext.HEALTH, EmotionalContext.CALM),
            new GroupSpec("Stock market crash warning", "Risk and danger in the finance sector right now", LifeContext.FINANCE, EmotionalContext.ANXIOUS),
            new GroupSpec("How does machine learning work", "A deep dive guide explaining neural networks", LifeContext.LEARNING, EmotionalContext.CURIOUS),
            new GroupSpec("Remember when we traveled to Japan", "A nostalgic throwback to our trip a decade ago", LifeContext.TRAVEL, EmotionalContext.NOSTALGIC)
    );

    @BeforeEach
    void seedTwentyItems() {
        user = userRepository.save(User.builder()
                .email("intel-test-" + System.nanoTime() + "@memoryvault.dev")
                .passwordHash("irrelevant")
                .displayName("Intelligence Test User")
                .timezone("UTC")
                .build());

        for (GroupSpec group : GROUPS) {
            for (int i = 0; i < 4; i++) {
                vaultItemRepository.save(VaultItem.builder()
                        .user(user)
                        .url("https://example.com/" + group.life() + "/" + i)
                        .title(group.title())
                        .summary(group.summary())
                        .contentType(ContentType.ARTICLE)
                        .status(ItemStatus.PROCESSED)
                        .emotionalContext(group.emotion())
                        .lifeContext(group.life())
                        .importanceScore(java.math.BigDecimal.valueOf(0.6))
                        .savedAt(Instant.now())
                        .viewCount(0)
                        .build());
            }
        }

        assertThat(vaultItemRepository.findByUserIdOrderBySavedAtDesc(user.getId(), org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                .isEqualTo(20);
    }

    @Test
    void contextDetector_ranksTopResultInExpectedGroup_forFiveDistinctQueries() {
        List<VaultItem> allItems = vaultItemRepository.findByUserIdOrderBySavedAtDesc(user.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent();

        Map<String, LifeContext> queryToExpectedLife = Map.of(
                "exciting new career job growth", LifeContext.CAREER,
                "mindfulness relax health meditation", LifeContext.HEALTH,
                "risk danger stock finance investment", LifeContext.FINANCE,
                "how does machine learning tutorial explained guide", LifeContext.LEARNING,
                "remember when travel trip decade throwback", LifeContext.TRAVEL
        );

        queryToExpectedLife.forEach((query, expectedLife) -> {
            List<ScoredVaultItem> ranked = contextDetector.rank(query, allItems, allItems.size());

            assertThat(ranked).isNotEmpty();
            ScoredVaultItem top = ranked.get(0);
            assertThat(top.item().getLifeContext())
                    .as("top result for query '%s' should be from the %s group", query, expectedLife)
                    .isEqualTo(expectedLife);
            assertThat(top.score()).isGreaterThan(ranked.get(ranked.size() - 1).score());
        });
    }

    @Test
    void contextDetector_isNotColdStart_atExactlyTwentyItems() {
        // 20 items is the documented cold-start threshold boundary (< 20 is cold start).
        assertThat(ContextDetector.COLD_START_THRESHOLD).isEqualTo(20);

        List<VaultItem> allItems = vaultItemRepository.findByUserIdOrderBySavedAtDesc(user.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent();
        List<ScoredVaultItem> ranked = contextDetector.rank("career job", allItems, allItems.size());

        // Real ranking (not cold-start recency/importance fallback) must favor the matching group.
        assertThat(ranked.get(0).item().getLifeContext()).isEqualTo(LifeContext.CAREER);
    }

    @Test
    void resurfaceEngine_appliesForgottenBonus_andRanksHighestScoreFirst() {
        List<VaultItem> items = new ArrayList<>();

        VaultItem recentViewed = save("recent-viewed", Instant.now(), 5);
        VaultItem oldForgotten = save("old-forgotten", Instant.now().minus(45, ChronoUnit.DAYS), 0);
        VaultItem oldViewed = save("old-viewed", Instant.now().minus(45, ChronoUnit.DAYS), 3);
        VaultItem midRecentUnviewed = save("mid-recent-unviewed", Instant.now().minus(5, ChronoUnit.DAYS), 0);
        items.add(recentViewed);
        items.add(oldForgotten);
        items.add(oldViewed);
        items.add(midRecentUnviewed);

        // Pass totalUserItemCount=20 to bypass the cold-start branch even though this
        // candidate list is small, isolating the decay/forgotten-bonus formula itself.
        // All four items share NEUTRAL/OTHER context (matching the empty query's fallback
        // classification), so contextMatch is equal across all of them here — ranking is
        // driven purely by recencyDecay, engagement, and the forgotten-bonus multiplier.
        List<ScoredVaultItem> ranked = resurfaceEngine.topResurfaceCandidates("", items, 20, 10);

        assertThat(ranked).hasSize(4);
        // The 30+ day, never-viewed item's 1.4x forgotten bonus is strong enough to
        // outrank even a same-day, already-viewed item — that dominance is the behavior
        // under test, not an incidental tie-break.
        assertThat(ranked.get(0).item().getId()).isEqualTo(oldForgotten.getId());

        double forgottenScore = scoreOf(ranked, oldForgotten.getId());
        double viewedScore = scoreOf(ranked, oldViewed.getId());
        assertThat(forgottenScore)
                .as("forgotten (never-viewed, 30+ days) item must outrank an equally-old but viewed item due to the 1.4x bonus")
                .isGreaterThan(viewedScore);

        for (int i = 0; i < ranked.size() - 1; i++) {
            assertThat(ranked.get(i).score()).isGreaterThanOrEqualTo(ranked.get(i + 1).score());
        }
    }

    @Test
    void forgottenGems_respectsThirtyDayThreshold_andExcludesViewedItems() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);

        VaultItem tooRecent = save("too-recent-29-days", Instant.now().minus(29, ChronoUnit.DAYS), 0);
        VaultItem oldEnoughUnviewed = save("old-enough-unviewed-31-days", Instant.now().minus(31, ChronoUnit.DAYS), 0);
        VaultItem oldEnoughButViewed = save("old-enough-viewed-31-days", Instant.now().minus(31, ChronoUnit.DAYS), 2);

        List<VaultItem> forgotten = vaultItemRepository.findForgottenCandidates(user.getId(), cutoff);
        List<Long> forgottenIds = forgotten.stream().map(VaultItem::getId).toList();

        assertThat(forgottenIds).contains(oldEnoughUnviewed.getId());
        assertThat(forgottenIds).doesNotContain(tooRecent.getId());
        assertThat(forgottenIds).doesNotContain(oldEnoughButViewed.getId());
    }

    private VaultItem save(String slug, Instant savedAt, int viewCount) {
        return vaultItemRepository.save(VaultItem.builder()
                .user(user)
                .url("https://example.com/resurface/" + slug)
                .title(slug)
                .contentType(ContentType.OTHER)
                .status(ItemStatus.PROCESSED)
                .emotionalContext(EmotionalContext.NEUTRAL)
                .lifeContext(LifeContext.OTHER)
                .importanceScore(java.math.BigDecimal.valueOf(0.5))
                .savedAt(savedAt)
                .viewCount(viewCount)
                .build());
    }

    private double scoreOf(List<ScoredVaultItem> ranked, Long itemId) {
        return ranked.stream()
                .filter(s -> s.item().getId().equals(itemId))
                .findFirst()
                .orElseThrow()
                .score();
    }
}
