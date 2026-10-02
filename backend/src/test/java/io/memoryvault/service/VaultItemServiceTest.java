package io.memoryvault.service;

import io.memoryvault.domain.User;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ItemSource;
import io.memoryvault.domain.enums.ItemStatus;
import io.memoryvault.dto.chrome.ChromeSearchResult;
import io.memoryvault.dto.vault.PagedResponse;
import io.memoryvault.dto.vault.SaveVaultItemRequest;
import io.memoryvault.dto.vault.SearchResultItem;
import io.memoryvault.dto.vault.VaultItemResponse;
import io.memoryvault.exception.ApiException;
import io.memoryvault.exception.ResourceNotFoundException;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.repository.VaultItemRepository;
import io.memoryvault.service.intelligence.ContextDetector;
import io.memoryvault.service.intelligence.ResurfaceEngine;
import io.memoryvault.service.intelligence.ScoredVaultItem;
import io.memoryvault.service.intelligence.VaultItemSavedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VaultItemServiceTest {

    @Mock
    private VaultItemRepository vaultItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContextDetector contextDetector;

    @Mock
    private ResurfaceEngine resurfaceEngine;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private VaultItemService vaultItemService;

    @BeforeEach
    void setUp() {
        vaultItemService = new VaultItemService(
                vaultItemRepository,
                userRepository,
                contextDetector,
                resurfaceEngine,
                eventPublisher
        );
    }

    @Test
    void save_defaultsSourceToWeb_andPublishesEvent() {
        User user = User.builder().id(1L).email("user@test.com").build();
        when(userRepository.getReferenceById(1L)).thenReturn(user);

        when(vaultItemRepository.save(any(VaultItem.class))).thenAnswer(i -> {
            VaultItem item = i.getArgument(0);
            item.setId(99L);
            return item;
        });

        SaveVaultItemRequest req = new SaveVaultItemRequest("https://example.com/article", null);
        VaultItemResponse response = vaultItemService.save(1L, req);

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.url()).isEqualTo("https://example.com/article");

        ArgumentCaptor<VaultItem> itemCaptor = ArgumentCaptor.forClass(VaultItem.class);
        verify(vaultItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getSource()).isEqualTo(ItemSource.WEB);

        ArgumentCaptor<VaultItemSavedEvent> eventCaptor = ArgumentCaptor.forClass(VaultItemSavedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().vaultItemId()).isEqualTo(99L);
    }

    @Test
    void saveFromChromeSelection_savesSummaryAndSource() {
        User user = User.builder().id(1L).build();
        when(userRepository.getReferenceById(1L)).thenReturn(user);

        when(vaultItemRepository.save(any(VaultItem.class))).thenAnswer(i -> {
            VaultItem item = i.getArgument(0);
            item.setId(101L);
            return item;
        });

        VaultItemResponse response = vaultItemService.saveFromChromeSelection(
                1L, "https://example.com/doc", "Selected highlight text"
        );

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.summary()).isEqualTo("Selected highlight text");

        ArgumentCaptor<VaultItem> itemCaptor = ArgumentCaptor.forClass(VaultItem.class);
        verify(vaultItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getSource()).isEqualTo(ItemSource.CHROME_EXTENSION);
    }

    @Test
    void getById_whenItemNotFound_throwsResourceNotFound() {
        when(vaultItemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vaultItemService.getById(1L, 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_whenBelongsToAnotherUser_throwsForbidden() {
        User owner = User.builder().id(2L).build();
        VaultItem item = VaultItem.builder().id(50L).user(owner).build();
        when(vaultItemRepository.findById(50L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> vaultItemService.getById(1L, 50L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void getById_whenOwnedByUser_incrementsViewCountAndUpdatesLastViewed() {
        User owner = User.builder().id(1L).build();
        VaultItem item = VaultItem.builder()
                .id(50L)
                .user(owner)
                .url("https://example.com")
                .viewCount(2)
                .build();
        when(vaultItemRepository.findById(50L)).thenReturn(Optional.of(item));
        when(vaultItemRepository.save(any(VaultItem.class))).thenAnswer(i -> i.getArgument(0));

        VaultItemResponse response = vaultItemService.getById(1L, 50L);

        assertThat(response.id()).isEqualTo(50L);
        assertThat(item.getViewCount()).isEqualTo(3);
        assertThat(item.getLastViewedAt()).isNotNull();
    }

    @Test
    void markRediscovered_updatesLastSurfacedAtAndIncrementsView() {
        User owner = User.builder().id(1L).build();
        VaultItem item = VaultItem.builder()
                .id(50L)
                .user(owner)
                .url("https://example.com")
                .viewCount(0)
                .build();
        when(vaultItemRepository.findById(50L)).thenReturn(Optional.of(item));
        when(vaultItemRepository.save(any(VaultItem.class))).thenAnswer(i -> i.getArgument(0));

        VaultItemResponse response = vaultItemService.markRediscovered(1L, 50L);

        assertThat(response.id()).isEqualTo(50L);
        assertThat(item.getViewCount()).isEqualTo(1);
        assertThat(item.getLastSurfacedAt()).isNotNull();
    }

    @Test
    void list_mapsPaginationCorrectly() {
        User user = User.builder().id(1L).build();
        VaultItem item1 = VaultItem.builder().id(1L).user(user).url("https://one.com").build();
        VaultItem item2 = VaultItem.builder().id(2L).user(user).url("https://two.com").build();

        var pageRequest = PageRequest.of(0, 10);
        when(vaultItemRepository.findByUserIdOrderBySavedAtDesc(1L, pageRequest))
                .thenReturn(new PageImpl<>(List.of(item1, item2), pageRequest, 2));

        PagedResponse<VaultItemResponse> page = vaultItemService.list(1L, 0, 10);

        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.page()).isEqualTo(0);
    }

    @Test
    void search_filtersItemsBelowSemanticSimilarityThreshold() {
        User user = User.builder().id(1L).build();
        VaultItem item1 = VaultItem.builder().id(1L).user(user).url("https://one.com").status(ItemStatus.PROCESSED).build();
        VaultItem item2 = VaultItem.builder().id(2L).user(user).url("https://two.com").status(ItemStatus.PROCESSED).build();

        when(vaultItemRepository.findByUserIdOrderBySavedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item1, item2)));

        ScoredVaultItem high = new ScoredVaultItem(item1, 0.85, "Strong keyword match");
        ScoredVaultItem low = new ScoredVaultItem(item2, 0.15, "Weak correlation"); // below 0.30 threshold

        when(contextDetector.rank("kubernetes", List.of(item1, item2), 2))
                .thenReturn(List.of(high, low));

        List<SearchResultItem> results = vaultItemService.search(1L, "kubernetes");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).item().id()).isEqualTo(1L);
        assertThat(results.get(0).score()).isEqualTo(new BigDecimal("0.8500"));
    }

    @Test
    void chromeSearch_limitsToFiveResults() {
        User user = User.builder().id(1L).build();
        Instant now = Instant.now();
        VaultItem item = VaultItem.builder()
                .id(1L)
                .user(user)
                .title("Spring Boot Guide")
                .url("https://spring.io")
                .source(ItemSource.WEB)
                .status(ItemStatus.PROCESSED)
                .savedAt(now.minus(5, ChronoUnit.DAYS))
                .importanceScore(new BigDecimal("0.9000"))
                .build();

        when(vaultItemRepository.findByUserIdOrderBySavedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));

        ScoredVaultItem scored = new ScoredVaultItem(item, 0.88, "Relevant article");
        when(contextDetector.rank("spring", List.of(item), 1))
                .thenReturn(List.of(scored));

        List<ChromeSearchResult> results = vaultItemService.chromeSearch(1L, "spring");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Spring Boot Guide");
        assertThat(results.get(0).daysSinceSaved()).isEqualTo(5);
        assertThat(results.get(0).contextScore()).isEqualTo(0.88);
    }
}
