package io.memoryvault.service.intelligence;

/**
 * Published after a new vault item is persisted, and consumed by
 * {@link ContentIntelligenceService#onVaultItemSaved} only once the saving transaction has
 * committed — that ordering is what prevents the enrichment read from racing (and missing)
 * the row it's meant to enrich.
 *
 * @param vaultItemId id of the newly-saved item to enrich
 */
public record VaultItemSavedEvent(Long vaultItemId) {
}
