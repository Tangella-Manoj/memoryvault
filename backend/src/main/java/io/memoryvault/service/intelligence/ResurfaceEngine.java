package io.memoryvault.service.intelligence;

import io.memoryvault.domain.ResurfaceEvent;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ResurfaceAction;
import io.memoryvault.repository.ResurfaceEventRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * score = (contextMatch * 0.40 + recencyDecay * 0.30 + engagementHistory * 0.30) * forgottenBonus
 * recencyDecay = e^(-0.015 * daysSinceSaved)
 * forgottenBonus = 1.4 when unseen for 30+ days and never viewed, otherwise 1.0
 */
@Service
public class ResurfaceEngine {

    private static final double RECENCY_DECAY_LAMBDA = 0.015;
    private static final double FORGOTTEN_BONUS = 1.4;
    private static final double NEUTRAL_ENGAGEMENT = 0.5;
    private static final int FORGOTTEN_DAYS_THRESHOLD = 30;

    private final ContextDetector contextDetector;
    private final ResurfaceEventRepository resurfaceEventRepository;

    /**
     * @param contextDetector          supplies the base context-match score each candidate
     *                                 is rescored from
     * @param resurfaceEventRepository source of each item's past resurface-event history,
     *                                 used to compute its engagement score
     */
    public ResurfaceEngine(ContextDetector contextDetector, ResurfaceEventRepository resurfaceEventRepository) {
        this.contextDetector = contextDetector;
        this.resurfaceEventRepository = resurfaceEventRepository;
    }

    /**
     * Ranks candidate vault items for resurfacing: starts from {@link ContextDetector}'s
     * context-match score, then applies recency decay, engagement history, and the
     * forgotten-item bonus described in this class's Javadoc.
     *
     * @param contextQuery        free-text context to match against (may be empty)
     * @param candidates          vault items eligible to resurface
     * @param totalUserItemCount  the user's total item count, forwarded to
     *                            {@link ContextDetector#rank} to decide cold-start
     * @param limit               maximum number of results to return
     * @return the top {@code limit} candidates sorted by descending resurface score
     */
    public List<ScoredVaultItem> topResurfaceCandidates(String contextQuery, List<VaultItem> candidates, int totalUserItemCount, int limit) {
        List<ScoredVaultItem> contextRanked = contextDetector.rank(contextQuery, candidates, totalUserItemCount);

        return contextRanked.stream()
                .map(scored -> rescoreForResurfacing(scored.item(), scored.score()))
                .sorted(Comparator.comparingDouble(ScoredVaultItem::score).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private ScoredVaultItem rescoreForResurfacing(VaultItem item, double contextMatch) {
        double recencyDecay = Math.exp(-RECENCY_DECAY_LAMBDA * daysSince(item.getSavedAt()));
        double engagement = engagementHistory(item);
        boolean forgotten = isForgotten(item);
        double bonus = forgotten ? FORGOTTEN_BONUS : 1.0;

        double raw = contextMatch * 0.40 + recencyDecay * 0.30 + engagement * 0.30;
        double finalScore = raw * bonus;

        String reason = forgotten
                ? "You saved this " + daysSince(item.getSavedAt()) + " days ago and never came back to it"
                : "Resurfacing based on recency and relevance";

        return new ScoredVaultItem(item, finalScore, reason);
    }

    private boolean isForgotten(VaultItem item) {
        boolean neverViewed = item.getViewCount() == null || item.getViewCount() == 0;
        return neverViewed && daysSince(item.getSavedAt()) > FORGOTTEN_DAYS_THRESHOLD;
    }

    private double engagementHistory(VaultItem item) {
        List<ResurfaceEvent> events = resurfaceEventRepository.findByVaultItemId(item.getId());
        if (events.isEmpty()) {
            return NEUTRAL_ENGAGEMENT;
        }
        long positive = events.stream()
                .filter(e -> e.getAction() == ResurfaceAction.VIEWED || e.getAction() == ResurfaceAction.SAVED_AGAIN)
                .count();
        return (double) positive / events.size();
    }

    private long daysSince(Instant instant) {
        return Duration.between(instant, Instant.now()).toDays();
    }
}
