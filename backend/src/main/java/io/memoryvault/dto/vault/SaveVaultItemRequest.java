package io.memoryvault.dto.vault;

import io.memoryvault.domain.enums.ItemSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveVaultItemRequest(
        @NotBlank @Size(max = 2048) String url,
        ItemSource source
) {
}
