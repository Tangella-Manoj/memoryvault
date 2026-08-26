package io.memoryvault.dto.vault;

import java.math.BigDecimal;

public record SearchResultItem(
        VaultItemResponse item,
        BigDecimal score,
        String reason
) {
}
