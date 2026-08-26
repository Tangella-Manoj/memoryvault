package io.memoryvault.service.intelligence;

import io.memoryvault.domain.VaultItem;

public record ScoredVaultItem(VaultItem item, double score, String reason) {
}
