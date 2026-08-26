package io.memoryvault.dto.chrome;

import io.memoryvault.domain.enums.ItemSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChromeQuickSaveRequest(
        @NotBlank
        @Size(max = 2048)
        @Pattern(regexp = "^https?://.+", message = "url must start with http:// or https://")
        String url,
        /** Optional origin override for auto-capture content scripts (Upgrade 1: INSTAGRAM, TWITTER). Defaults to CHROME_EXTENSION. */
        ItemSource source
) {
}
