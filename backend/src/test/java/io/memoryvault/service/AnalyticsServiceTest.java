package io.memoryvault.service;

import io.memoryvault.domain.Tag;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.dto.vault.AnalyticsResponse;
import io.memoryvault.repository.VaultItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private VaultItemRepository vaultItemRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(vaultItemRepository);
    }

    @Test
    void forUser_emptyVault_returnsZerosAndEmptyBreakdowns() {
        when(vaultItemRepository.countByUserId(1L)).thenReturn(0L);
        when(vaultItemRepository.avgImportanceScoreByUserId(1L)).thenReturn(null);
        when(vaultItemRepository.countByContentType(1L)).thenReturn(List.of());
        when(vaultItemRepository.countByEmotionalContext(1L)).thenReturn(List.of());
        when(vaultItemRepository.findByUserIdOrderBySavedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        AnalyticsResponse response = analyticsService.forUser(1L);

        assertThat(response.totalItems()).isEqualTo(0);
        assertThat(response.averageImportanceScore()).isEqualTo(0.0);
        assertThat(response.intelligenceScore()).isEqualTo(0.0);
        assertThat(response.itemsByContentType()).isEmpty();
        assertThat(response.itemsByEmotionalContext()).isEmpty();
        assertThat(response.topTags()).isEmpty();
        assertThat(response.savesByDayOfWeek()).isEmpty();
    }

    @Test
    void forUser_populatedVault_computesMetricsAndTopTagsCorrectly() {
        when(vaultItemRepository.countByUserId(1L)).thenReturn(2L);
        when(vaultItemRepository.avgImportanceScoreByUserId(1L)).thenReturn(0.75);

        Object[] typeRow = new Object[]{"ARTICLE", 2L};
        when(vaultItemRepository.countByContentType(1L)).thenReturn(List.<Object[]>of(typeRow));

        Object[] moodRow = new Object[]{"CURIOUS", 2L};
        when(vaultItemRepository.countByEmotionalContext(1L)).thenReturn(List.<Object[]>of(moodRow));

        Tag tagJava = Tag.builder().id(1L).name("java").build();
        Tag tagSpring = Tag.builder().id(2L).name("spring").build();

        VaultItem item1 = VaultItem.builder()
                .id(1L)
                .savedAt(Instant.parse("2026-10-02T10:00:00Z"))
                .tags(Set.of(tagJava, tagSpring))
                .build();
        VaultItem item2 = VaultItem.builder()
                .id(2L)
                .savedAt(Instant.parse("2026-10-02T12:00:00Z"))
                .tags(Set.of(tagJava))
                .build();

        when(vaultItemRepository.findByUserIdOrderBySavedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item1, item2)));

        AnalyticsResponse response = analyticsService.forUser(1L);

        assertThat(response.totalItems()).isEqualTo(2);
        assertThat(response.averageImportanceScore()).isEqualTo(0.75);
        assertThat(response.intelligenceScore()).isEqualTo(75.0);
        assertThat(response.itemsByContentType()).containsEntry("ARTICLE", 2L);
        assertThat(response.itemsByEmotionalContext()).containsEntry("CURIOUS", 2L);

        assertThat(response.topTags()).hasSize(2);
        assertThat(response.topTags().get(0).tag()).isEqualTo("java");
        assertThat(response.topTags().get(0).count()).isEqualTo(2L);
        assertThat(response.topTags().get(1).tag()).isEqualTo("spring");
        assertThat(response.topTags().get(1).count()).isEqualTo(1L);
    }
}
