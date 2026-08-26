package io.memoryvault.dto.vault;

import java.time.LocalDate;
import java.util.List;

public record DigestResponse(
        LocalDate digestDate,
        List<DigestEntry> items
) {
    public record DigestEntry(VaultItemResponse item, String category) {
    }
}
