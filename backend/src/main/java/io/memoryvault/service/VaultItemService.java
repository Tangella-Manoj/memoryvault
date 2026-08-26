package io.memoryvault.service;

import io.memoryvault.domain.User;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ItemSource;
import io.memoryvault.domain.enums.ItemStatus;
import io.memoryvault.dto.vault.SaveVaultItemRequest;
import io.memoryvault.dto.vault.VaultItemResponse;
import io.memoryvault.exception.ResourceNotFoundException;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.repository.VaultItemRepository;
import io.memoryvault.service.intelligence.ContentIntelligenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VaultItemService {

    private final VaultItemRepository vaultItemRepository;
    private final UserRepository userRepository;
    private final ContentIntelligenceService contentIntelligenceService;

    public VaultItemService(
            VaultItemRepository vaultItemRepository,
            UserRepository userRepository,
            ContentIntelligenceService contentIntelligenceService
    ) {
        this.vaultItemRepository = vaultItemRepository;
        this.userRepository = userRepository;
        this.contentIntelligenceService = contentIntelligenceService;
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
        contentIntelligenceService.enrich(item.getId());

        return VaultItemResponse.from(item);
    }

    @Transactional(readOnly = true)
    public VaultItemResponse getById(Long userId, Long itemId) {
        VaultItem item = vaultItemRepository.findById(itemId)
                .filter(v -> v.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("VaultItem", itemId));
        return VaultItemResponse.from(item);
    }
}
