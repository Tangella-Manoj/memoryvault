package io.memoryvault.service;

import io.memoryvault.domain.User;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ItemSource;
import io.memoryvault.domain.enums.ItemStatus;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.repository.VaultItemRepository;
import io.memoryvault.service.intelligence.VaultItemSavedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists one imported URL as a vault item and publishes {@link VaultItemSavedEvent}.
 * Deliberately a separate bean from {@link BulkImportWorker}: content enrichment listens
 * for this event via {@code @TransactionalEventListener(phase = AFTER_COMMIT)}, which only
 * fires when the event is published from inside a real Spring-managed transaction. Calling
 * this method as {@code this.processOne(...)} from within {@code BulkImportWorker} (a
 * same-class call) would bypass the {@code @Transactional} proxy entirely — no transaction
 * would ever be active when the event publishes, and with
 * {@code @TransactionalEventListener}'s default {@code fallbackExecution=false}, the
 * listener would silently never run. This was a real bug: it shipped, made the whole bulk
 * import job report {@code DONE} correctly, but left every imported item's enrichment
 * silently dead. Keeping this as a distinct bean is what makes the cross-bean call from
 * {@link BulkImportWorker} go through the real proxy.
 */
@Component
public class VaultItemImportPersister {

    private final VaultItemRepository vaultItemRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public VaultItemImportPersister(
            VaultItemRepository vaultItemRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.vaultItemRepository = vaultItemRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates one {@code PROCESSING} vault item for {@code url} and publishes the saved
     * event that content enrichment listens for, inside a real transaction so the
     * after-commit listener actually fires.
     *
     * @param userId the owning user
     * @param url    the URL to save
     * @return the id of the newly created item
     */
    @Transactional
    public Long processOne(Long userId, String url) {
        User user = userRepository.getReferenceById(userId);
        VaultItem item = VaultItem.builder()
                .user(user)
                .url(url)
                .status(ItemStatus.PROCESSING)
                .source(ItemSource.BULK_IMPORT)
                .build();
        item = vaultItemRepository.save(item);
        eventPublisher.publishEvent(new VaultItemSavedEvent(item.getId()));
        return item.getId();
    }
}
