package io.memoryvault.service;

import io.memoryvault.domain.User;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ItemSource;
import io.memoryvault.domain.enums.ItemStatus;
import io.memoryvault.dto.vault.SaveVaultItemRequest;
import io.memoryvault.dto.vault.SearchResultItem;
import io.memoryvault.dto.vault.VaultItemResponse;
import io.memoryvault.exception.ResourceNotFoundException;
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
        return save(userId, new SaveVaultItemRequest(url, ItemSource.CHROME_EXTENSION));
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

    @Transactional(readOnly = true)
    public VaultItemResponse getById(Long userId, Long itemId) {
        VaultItem item = vaultItemRepository.findById(itemId)
                .filter(v -> v.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("VaultItem", itemId));
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
        VaultItem item = vaultItemRepository.findById(itemId)
                .filter(v -> v.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("VaultItem", itemId));

        item.setViewCount((item.getViewCount() != null ? item.getViewCount() : 0) + 1);
        item.setLastSurfacedAt(java.time.Instant.now());
        vaultItemRepository.save(item);

        return VaultItemResponse.from(item);
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
