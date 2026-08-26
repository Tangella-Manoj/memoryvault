package io.memoryvault.service.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.memoryvault.domain.ResurfaceEvent;
import io.memoryvault.domain.User;
import io.memoryvault.domain.UserBehaviorPattern;
import io.memoryvault.domain.UserContext;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ResurfaceAction;
import io.memoryvault.repository.ResurfaceEventRepository;
import io.memoryvault.repository.UserBehaviorPatternRepository;
import io.memoryvault.repository.UserContextRepository;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.repository.VaultItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nightly pass that turns raw engagement signals (resurface events, view counts) into
 * per-user context weights (user_contexts) and refreshed per-item importance scores.
 */
@Service
public class BehaviorLearnerService {

    private static final Logger log = LoggerFactory.getLogger(BehaviorLearnerService.class);

    private final UserRepository userRepository;
    private final VaultItemRepository vaultItemRepository;
    private final ResurfaceEventRepository resurfaceEventRepository;
    private final UserContextRepository userContextRepository;
    private final UserBehaviorPatternRepository userBehaviorPatternRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BehaviorLearnerService(
            UserRepository userRepository,
            VaultItemRepository vaultItemRepository,
            ResurfaceEventRepository resurfaceEventRepository,
            UserContextRepository userContextRepository,
            UserBehaviorPatternRepository userBehaviorPatternRepository
    ) {
        this.userRepository = userRepository;
        this.vaultItemRepository = vaultItemRepository;
        this.resurfaceEventRepository = resurfaceEventRepository;
        this.userContextRepository = userContextRepository;
        this.userBehaviorPatternRepository = userBehaviorPatternRepository;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void runNightlyForAllUsers() {
        List<User> users = userRepository.findAll();
        log.info("BehaviorLearner: running nightly pass for {} users", users.size());
        for (User user : users) {
            runForUser(user.getId());
        }
    }

    @Transactional
    public Map<String, Object> runForUser(Long userId) {
        List<VaultItem> items = vaultItemRepository.findByUserIdOrderBySavedAtDesc(userId, org.springframework.data.domain.Pageable.unpaged()).getContent();

        Map<String, Long> lifeContextCounts = new HashMap<>();
        Map<String, Long> emotionalContextCounts = new HashMap<>();
        int refreshedItems = 0;

        for (VaultItem item : items) {
            lifeContextCounts.merge(item.getLifeContext().name(), 1L, Long::sum);
            emotionalContextCounts.merge(item.getEmotionalContext().name(), 1L, Long::sum);

            BigDecimal refreshed = recomputeImportance(item);
            item.setImportanceScore(refreshed);
            refreshedItems++;
        }
        vaultItemRepository.saveAll(items);

        upsertContextWeights(userId, "life_context", lifeContextCounts, items.size());
        upsertContextWeights(userId, "emotional_context", emotionalContextCounts, items.size());

        double intelligenceScore = items.stream()
                .mapToDouble(i -> i.getImportanceScore() != null ? i.getImportanceScore().doubleValue() : 0.0)
                .average()
                .orElse(0.0) * 100.0;

        Map<String, Object> summary = new HashMap<>();
        summary.put("userId", userId);
        summary.put("itemsRefreshed", refreshedItems);
        summary.put("intelligenceScore", Math.round(intelligenceScore * 100.0) / 100.0);
        summary.put("lifeContextCounts", lifeContextCounts);
        summary.put("emotionalContextCounts", emotionalContextCounts);

        try {
            User userRef = userRepository.getReferenceById(userId);
            userBehaviorPatternRepository.save(UserBehaviorPattern.builder()
                    .user(userRef)
                    .patternType("nightly_summary")
                    .patternValue(objectMapper.writeValueAsString(summary))
                    .computedAt(Instant.now())
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to persist behavior pattern for user {}: {}", userId, ex.getMessage());
        }

        return summary;
    }

    private BigDecimal recomputeImportance(VaultItem item) {
        List<ResurfaceEvent> events = resurfaceEventRepository.findByVaultItemId(item.getId());
        double engagementRatio = 0.5;
        if (!events.isEmpty()) {
            long positive = events.stream()
                    .filter(e -> e.getAction() == ResurfaceAction.VIEWED || e.getAction() == ResurfaceAction.SAVED_AGAIN)
                    .count();
            engagementRatio = (double) positive / events.size();
        }
        double viewSignal = Math.min((item.getViewCount() != null ? item.getViewCount() : 0) / 5.0, 1.0);

        double base = item.getImportanceScore() != null ? item.getImportanceScore().doubleValue() : 0.5;
        double blended = base * 0.5 + engagementRatio * 0.3 + viewSignal * 0.2;

        return BigDecimal.valueOf(Math.min(blended, 1.0)).setScale(4, RoundingMode.HALF_UP);
    }

    private void upsertContextWeights(Long userId, String prefix, Map<String, Long> counts, int total) {
        if (total == 0) {
            return;
        }
        User userRef = userRepository.getReferenceById(userId);
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            String contextType = prefix + ":" + entry.getKey();
            BigDecimal weight = BigDecimal.valueOf((double) entry.getValue() / total).setScale(4, RoundingMode.HALF_UP);

            UserContext context = userContextRepository.findByUserIdAndContextType(userId, contextType)
                    .orElseGet(() -> UserContext.builder().user(userRef).contextType(contextType).build());
            context.setWeight(weight);
            userContextRepository.save(context);
        }
    }
}
