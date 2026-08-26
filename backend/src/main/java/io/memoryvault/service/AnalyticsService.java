package io.memoryvault.service;

import io.memoryvault.domain.Tag;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.dto.vault.AnalyticsResponse;
import io.memoryvault.repository.VaultItemRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final VaultItemRepository vaultItemRepository;

    public AnalyticsService(VaultItemRepository vaultItemRepository) {
        this.vaultItemRepository = vaultItemRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse forUser(Long userId) {
        List<VaultItem> items = vaultItemRepository.findByUserIdOrderBySavedAtDesc(userId, Pageable.unpaged()).getContent();

        double avgImportance = items.stream()
                .mapToDouble(i -> i.getImportanceScore() != null ? i.getImportanceScore().doubleValue() : 0.0)
                .average()
                .orElse(0.0);

        Map<String, Long> byDayOfWeek = items.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getSavedAt().atZone(ZoneOffset.UTC).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        Map<String, Long> byContentType = items.stream()
                .collect(Collectors.groupingBy(i -> i.getContentType().name(), LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> byEmotionalContext = items.stream()
                .collect(Collectors.groupingBy(i -> i.getEmotionalContext().name(), LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> tagCounts = items.stream()
                .flatMap(i -> i.getTags().stream())
                .map(Tag::getName)
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

        List<AnalyticsResponse.TagCount> topTags = tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20)
                .map(e -> new AnalyticsResponse.TagCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        double intelligenceScore = avgImportance * 100.0;

        return new AnalyticsResponse(
                items.size(),
                Math.round(avgImportance * 10000.0) / 10000.0,
                Math.round(intelligenceScore * 100.0) / 100.0,
                byDayOfWeek,
                byContentType,
                byEmotionalContext,
                topTags
        );
    }
}
