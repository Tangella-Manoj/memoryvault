package io.memoryvault.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.memoryvault.domain.DailyDigest;
import io.memoryvault.domain.User;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ItemStatus;
import io.memoryvault.dto.vault.DigestResponse;
import io.memoryvault.dto.vault.VaultItemResponse;
import io.memoryvault.repository.DailyDigestRepository;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.repository.VaultItemRepository;
import io.memoryvault.service.intelligence.ResurfaceEngine;
import io.memoryvault.service.intelligence.ScoredVaultItem;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class DigestService {

    private final DailyDigestRepository dailyDigestRepository;
    private final VaultItemRepository vaultItemRepository;
    private final UserRepository userRepository;
    private final ResurfaceEngine resurfaceEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    public DigestService(
            DailyDigestRepository dailyDigestRepository,
            VaultItemRepository vaultItemRepository,
            UserRepository userRepository,
            ResurfaceEngine resurfaceEngine
    ) {
        this.dailyDigestRepository = dailyDigestRepository;
        this.vaultItemRepository = vaultItemRepository;
        this.userRepository = userRepository;
        this.resurfaceEngine = resurfaceEngine;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void generateForUsersAt8am() {
        for (User user : userRepository.findAll()) {
            ZoneId zone = safeZone(user.getTimezone());
            int localHour = Instant.now().atZone(zone).getHour();
            if (localHour == 8) {
                LocalDate localDate = Instant.now().atZone(zone).toLocalDate();
                if (dailyDigestRepository.findByUserIdAndDigestDate(user.getId(), localDate).isEmpty()) {
                    generateAndStore(user.getId(), localDate);
                }
            }
        }
    }

    @Transactional
    public DigestResponse getOrGenerateToday(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        LocalDate today = Instant.now().atZone(safeZone(user.getTimezone())).toLocalDate();

        DailyDigest digest = dailyDigestRepository.findByUserIdAndDigestDate(userId, today)
                .orElseGet(() -> generateAndStore(userId, today));

        return toResponse(digest);
    }

    @Transactional
    DailyDigest generateAndStore(Long userId, LocalDate date) {
        List<VaultItem> all = vaultItemRepository.findByUserIdOrderBySavedAtDesc(userId, Pageable.unpaged())
                .getContent()
                .stream()
                .filter(v -> v.getStatus() == ItemStatus.PROCESSED)
                .collect(Collectors.toList());

        Map<String, List<Long>> categorized = new LinkedHashMap<>();
        List<Long> used = new ArrayList<>();

        pickOneYearAgo(all, used).ifPresent(id -> categorized.put("one_year_ago", List.of(id)));
        List<Long> patternMatched = pickPatternMatched(all, used, userId, 2);
        if (!patternMatched.isEmpty()) {
            categorized.put("pattern_match", patternMatched);
        }
        pickNeverViewed(all, used).ifPresent(id -> categorized.put("never_viewed", List.of(id)));
        pickRandomRediscovery(all, used).ifPresent(id -> categorized.put("random_rediscovery", List.of(id)));

        DailyDigest digest = DailyDigest.builder()
                .user(userRepository.getReferenceById(userId))
                .digestDate(date)
                .itemIds(writeJson(categorized))
                .build();
        return dailyDigestRepository.save(digest);
    }

    private java.util.Optional<Long> pickOneYearAgo(List<VaultItem> all, List<Long> used) {
        Instant now = Instant.now();
        return all.stream()
                .filter(v -> !used.contains(v.getId()))
                .filter(v -> ChronoUnit.DAYS.between(v.getSavedAt(), now) >= 350
                        && ChronoUnit.DAYS.between(v.getSavedAt(), now) <= 380)
                .findFirst()
                .map(v -> { used.add(v.getId()); return v.getId(); });
    }

    private List<Long> pickPatternMatched(List<VaultItem> all, List<Long> used, Long userId, int count) {
        List<VaultItem> candidates = all.stream().filter(v -> !used.contains(v.getId())).collect(Collectors.toList());
        List<ScoredVaultItem> scored = resurfaceEngine.topResurfaceCandidates("", candidates, all.size(), count);
        List<Long> ids = scored.stream().map(s -> s.item().getId()).collect(Collectors.toList());
        used.addAll(ids);
        return ids;
    }

    private java.util.Optional<Long> pickNeverViewed(List<VaultItem> all, List<Long> used) {
        return all.stream()
                .filter(v -> !used.contains(v.getId()))
                .filter(v -> v.getViewCount() == null || v.getViewCount() == 0)
                .findFirst()
                .map(v -> { used.add(v.getId()); return v.getId(); });
    }

    private java.util.Optional<Long> pickRandomRediscovery(List<VaultItem> all, List<Long> used) {
        List<VaultItem> pool = all.stream().filter(v -> !used.contains(v.getId())).collect(Collectors.toList());
        if (pool.isEmpty()) {
            return java.util.Optional.empty();
        }
        VaultItem chosen = pool.get(random.nextInt(pool.size()));
        used.add(chosen.getId());
        return java.util.Optional.of(chosen.getId());
    }

    private DigestResponse toResponse(DailyDigest digest) {
        Map<String, List<Long>> categorized = readJson(digest.getItemIds());
        List<DigestResponse.DigestEntry> entries = new ArrayList<>();

        for (Map.Entry<String, List<Long>> entry : categorized.entrySet()) {
            for (Long id : entry.getValue()) {
                vaultItemRepository.findById(id).ifPresent(item ->
                        entries.add(new DigestResponse.DigestEntry(VaultItemResponse.from(item), entry.getKey())));
            }
        }

        return new DigestResponse(digest.getDigestDate(), entries);
    }

    private ZoneId safeZone(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (Exception ex) {
            return ZoneOffset.UTC;
        }
    }

    private String writeJson(Map<String, List<Long>> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, List<Long>> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, List<Long>>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
