package io.memoryvault.service.intelligence;

import io.memoryvault.domain.enums.EmotionalContext;
import io.memoryvault.domain.enums.LifeContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic keyword-based classifier for {@link EmotionalContext} and {@link LifeContext}.
 * Scores a text against a fixed keyword dictionary per context value and picks the
 * highest-scoring match; ties keep the first-declared value, and text matching nothing
 * (or blank/null input) falls back to {@code NEUTRAL}/{@code OTHER}. Used both when a
 * newly-saved item is enriched and when classifying a free-text search query.
 */
@Component
public class ContextKeywordDetector {

    private static final Map<EmotionalContext, String[]> EMOTIONAL_KEYWORDS = new LinkedHashMap<>();
    private static final Map<LifeContext, String[]> LIFE_KEYWORDS = new LinkedHashMap<>();

    static {
        EMOTIONAL_KEYWORDS.put(EmotionalContext.INSPIRED, new String[]{"inspiring", "breakthrough", "visionary", "motivation", "achieve"});
        EMOTIONAL_KEYWORDS.put(EmotionalContext.CURIOUS, new String[]{"how does", "explained", "guide", "deep dive", "why"});
        EMOTIONAL_KEYWORDS.put(EmotionalContext.ANXIOUS, new String[]{"warning", "crisis", "risk", "danger", "urgent"});
        EMOTIONAL_KEYWORDS.put(EmotionalContext.NOSTALGIC, new String[]{"remember when", "throwback", "anniversary", "reunion", "decade"});
        EMOTIONAL_KEYWORDS.put(EmotionalContext.EXCITED, new String[]{"launch", "announcing", "release", "exciting", "new feature"});
        EMOTIONAL_KEYWORDS.put(EmotionalContext.CALM, new String[]{"mindfulness", "relax", "meditation", "peaceful", "slow living"});

        LIFE_KEYWORDS.put(LifeContext.CAREER, new String[]{"job", "career", "resume", "interview", "salary", "promotion"});
        LIFE_KEYWORDS.put(LifeContext.HEALTH, new String[]{"health", "fitness", "workout", "diet", "medical", "sleep"});
        LIFE_KEYWORDS.put(LifeContext.RELATIONSHIPS, new String[]{"relationship", "friendship", "dating", "marriage", "family"});
        LIFE_KEYWORDS.put(LifeContext.FINANCE, new String[]{"invest", "budget", "savings", "stock", "money", "finance"});
        LIFE_KEYWORDS.put(LifeContext.LEARNING, new String[]{"tutorial", "course", "learn", "study", "certification"});
        LIFE_KEYWORDS.put(LifeContext.CREATIVITY, new String[]{"design", "art", "creative", "music", "writing", "craft"});
        LIFE_KEYWORDS.put(LifeContext.TRAVEL, new String[]{"travel", "trip", "destination", "flight", "itinerary"});
        LIFE_KEYWORDS.put(LifeContext.HOME, new String[]{"home", "recipe", "kitchen", "furniture", "decor", "garden"});
    }

    /**
     * @param text free text to classify (page content, or a search query)
     * @return the emotional context whose keywords best match {@code text}, or
     *         {@code NEUTRAL} when nothing matches or {@code text} is blank/null
     */
    public EmotionalContext detectEmotional(String text) {
        return detect(text, EMOTIONAL_KEYWORDS, EmotionalContext.NEUTRAL);
    }

    /**
     * @param text free text to classify (page content, or a search query)
     * @return the life context whose keywords best match {@code text}, or {@code OTHER}
     *         when nothing matches or {@code text} is blank/null
     */
    public LifeContext detectLife(String text) {
        return detect(text, LIFE_KEYWORDS, LifeContext.OTHER);
    }

    private <T> T detect(String text, Map<T, String[]> dictionary, T fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String lower = text.toLowerCase();

        T best = fallback;
        int bestScore = 0;
        for (Map.Entry<T, String[]> entry : dictionary.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = entry.getKey();
            }
        }
        return best;
    }
}
