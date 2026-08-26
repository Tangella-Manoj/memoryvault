package io.memoryvault.service;

import io.memoryvault.domain.User;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ItemSource;
import io.memoryvault.domain.enums.ItemStatus;
import io.memoryvault.dto.vault.SaveVaultItemRequest;
import io.memoryvault.dto.vault.SearchResultItem;
import io.memoryvault.dto.vault.VaultItemResponse;
import io.memoryvault.exception.ApiException;
import io.memoryvault.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.repository.VaultItemRepository;
import io.memoryvault.service.intelligence.ContextDetector;
import io.memoryvault.service.intelligence.ResurfaceEngine;
import io.memoryvault.service.intelligence.ScoredVaultItem;
import io.memoryvault.service.intelligence.VaultItemSavedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class VaultItemService {

    private final VaultItemRepository vaultItemRepository;
    private final UserRepository userRepository;
    private final ContextDetector contextDetector;
    private final ResurfaceEngine resurfaceEngine;
    private final ApplicationEventPublisher eventPublisher;

    public VaultItemService(
            VaultItemRepository vaultItemRepository,
            UserRepository userRepository,
            ContextDetector contextDetector,
            ResurfaceEngine resurfaceEngine,
            ApplicationEventPublisher eventPublisher
    ) {
        this.vaultItemRepository = vaultItemRepository;
        this.userRepository = userRepository;
        this.contextDetector = contextDetector;
        this.resurfaceEngine = resurfaceEngine;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public VaultItemResponse save(Long userId, SaveVaultItemRequest request) {
        User user = userRepository.getReferenceById(userId);

        VaultItem item = VaultItem.builder()
                .user(user)
                .url(request.url())
                .status(ItemStatus.PROCESSING)
                .source(request.source() != null ? request.source() : ItemSource.WEB)
                .build();

        item = vaultItemRepository.save(item);
        eventPublisher.publishEvent(new VaultItemSavedEvent(item.getId()));

        return VaultItemResponse.from(item);
    }

    @Transactional
    public VaultItemResponse saveFromChromeQuick(Long userId, String url) {
        return saveFromChromeQuick(userId, url, ItemSource.CHROME_EXTENSION);
    }

    @Transactional
    public VaultItemResponse saveFromChromeQuick(Long userId, String url, ItemSource source) {
        return save(userId, new SaveVaultItemRequest(url, source != null ? source : ItemSource.CHROME_EXTENSION));
    }

    @Transactional
    public VaultItemResponse saveFromChromeSelection(Long userId, String url, String selectedText) {
        User user = userRepository.getReferenceById(userId);

        VaultItem item = VaultItem.builder()
                .user(user)
                .url(url)
                .summary(selectedText)
                .status(ItemStatus.PROCESSING)
                .source(ItemSource.CHROME_EXTENSION)
                .build();

        item = vaultItemRepository.save(item);
        eventPublisher.publishEvent(new VaultItemSavedEvent(item.getId()));

        return VaultItemResponse.from(item);
    }

    @Transactional(readOnly = true)
    public io.memoryvault.dto.vault.PagedResponse<VaultItemResponse> list(Long userId, int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size);
        var result = vaultItemRepository.findByUserIdOrderBySavedAtDesc(userId, pageable);

        return new io.memoryvault.dto.vault.PagedResponse<>(
                result.getContent().stream().map(VaultItemResponse::from).collect(Collectors.toList()),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional
    public VaultItemResponse getById(Long userId, Long itemId) {
        VaultItem item = requireOwnedItem(userId, itemId);

        item.setViewCount((item.getViewCount() != null ? item.getViewCount() : 0) + 1);
        item.setLastViewedAt(java.time.Instant.now());
        vaultItemRepository.save(item);

        return VaultItemResponse.from(item);
    }

    @Transactional(readOnly = true)
    public List<SearchResultItem> search(Long userId, String query) {
        List<VaultItem> candidates = processedItemsFor(userId);
        int total = candidates.size();

        return contextDetector.rank(query, candidates, total).stream()
                .map(this::toSearchResult)
                .collect(Collectors.toList());
    }

    /**
     * Search formatted for the Chrome extension's contextual overlay (Upgrade 1): top 5
     * results, flat snake_case shape, includes the raw context score so the caller can
     * apply its own display threshold (the overlay only renders results above 0.4).
     *
     * @param userId the owning user
     * @param query  free-text query, typically the user's live search-engine input
     * @return up to 5 ranked results
     */
    @Transactional(readOnly = true)
    public List<io.memoryvault.dto.chrome.ChromeSearchResult> chromeSearch(Long userId, String query) {
        List<VaultItem> candidates = processedItemsFor(userId);
        int total = candidates.size();

        return contextDetector.rank(query, candidates, total).stream()
                .limit(5)
                .map(scored -> {
                    VaultItem item = scored.item();
                    long daysSince = java.time.temporal.ChronoUnit.DAYS.between(item.getSavedAt(), java.time.Instant.now());
                    return new io.memoryvault.dto.chrome.ChromeSearchResult(
                            item.getTitle(),
                            item.getSummary(),
                            item.getUrl(),
                            item.getSource().name(),
                            daysSince,
                            item.getImportanceScore(),
                            scored.reason(),
                            scored.score()
                    );
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SearchResultItem> resurfaceFeed(Long userId, int limit) {
        List<VaultItem> candidates = processedItemsFor(userId);
        int total = candidates.size();

        return resurfaceEngine.topResurfaceCandidates("", candidates, total, limit).stream()
                .map(this::toSearchResult)
                .collect(Collectors.toList());
    }

    @Transactional
    public VaultItemResponse markRediscovered(Long userId, Long itemId) {
        VaultItem item = requireOwnedItem(userId, itemId);

        item.setViewCount((item.getViewCount() != null ? item.getViewCount() : 0) + 1);
        item.setLastSurfacedAt(java.time.Instant.now());
        vaultItemRepository.save(item);

        return VaultItemResponse.from(item);
    }

    /**
     * Loads a vault item and enforces per-user ownership, distinguishing the two failure
     * cases the way callers actually need: an id nobody owns is {@code 404} (nothing to
     * find), while an id that belongs to a different user is {@code 403} (found, but not
     * yours) — deliberately not collapsed into a single 404, since callers here (item
     * detail, rediscover) are expected to react differently to "doesn't exist" vs.
     * "exists but isn't mine."
     *
     * @param userId the caller
     * @param itemId the item being accessed
     * @return the item, guaranteed owned by {@code userId}
     * @throws ResourceNotFoundException if no item with this id exists at all
     * @throws ApiException              with 403 FORBIDDEN if the item exists but belongs to another user
     */
    private VaultItem requireOwnedItem(Long userId, Long itemId) {
        VaultItem item = vaultItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("VaultItem", itemId));

        if (!item.getUser().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this item");
        }

        return item;
    }

    @Transactional(readOnly = true)
    public List<VaultItemResponse> forgottenGems(Long userId) {
        java.time.Instant cutoff = java.time.Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        return vaultItemRepository.findForgottenCandidates(userId, cutoff).stream()
                .map(VaultItemResponse::from)
                .collect(Collectors.toList());
    }

    private List<VaultItem> processedItemsFor(Long userId) {
        return vaultItemRepository.findByUserIdOrderBySavedAtDesc(userId, Pageable.unpaged())
                .getContent()
                .stream()
                .filter(v -> v.getStatus() == ItemStatus.PROCESSED)
                .collect(Collectors.toList());
    }

    private SearchResultItem toSearchResult(ScoredVaultItem scored) {
        return new SearchResultItem(
                VaultItemResponse.from(scored.item()),
                java.math.BigDecimal.valueOf(scored.score()).setScale(4, java.math.RoundingMode.HALF_UP),
                scored.reason()
        );
    }
}
