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
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final VaultItemRepository vaultItemRepository;

    public AnalyticsService(VaultItemRepository vaultItemRepository) {
        this.vaultItemRepository = vaultItemRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse forUser(Long userId) {
        long totalItems = vaultItemRepository.countByUserId(userId);
        Double rawAvg = vaultItemRepository.avgImportanceScoreByUserId(userId);
        double avgImportance = rawAvg != null ? rawAvg : 0.0;
        double intelligenceScore = avgImportance * 100.0;

        Map<String, Long> byContentType = new LinkedHashMap<>();
        for (Object[] row : vaultItemRepository.countByContentType(userId)) {
            if (row != null && row.length >= 2 && row[0] != null) {
                byContentType.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        Map<String, Long> byEmotionalContext = new LinkedHashMap<>();
        for (Object[] row : vaultItemRepository.countByEmotionalContext(userId)) {
            if (row != null && row.length >= 2 && row[0] != null) {
                byEmotionalContext.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        List<VaultItem> items = vaultItemRepository.findByUserIdOrderBySavedAtDesc(userId, Pageable.unpaged()).getContent();

        Map<String, Long> byDayOfWeek = items.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getSavedAt().atZone(ZoneOffset.UTC).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        Map<String, Long> tagCounts = items.stream()
                .flatMap(i -> i.getTags().stream())
                .map(Tag::getName)
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

        List<AnalyticsResponse.TagCount> topTags = tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20)
                .map(e -> new AnalyticsResponse.TagCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        return new AnalyticsResponse(
                totalItems,
                Math.round(avgImportance * 10000.0) / 10000.0,
                Math.round(intelligenceScore * 100.0) / 100.0,
                byDayOfWeek,
                byContentType,
                byEmotionalContext,
                topTags
        );
    }
}
