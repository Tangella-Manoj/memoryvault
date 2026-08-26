package io.memoryvault.dto.vault;

import io.memoryvault.domain.enums.ItemSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveVaultItemRequest(
        @NotBlank
        @Size(max = 2048)
        @Pattern(regexp = "^https?://.+", message = "url must start with http:// or https://")
        String url,
        ItemSource source
) {
}
