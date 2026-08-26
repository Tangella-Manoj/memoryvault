package io.memoryvault.dto.chrome;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChromeQuickSaveRequest(
        @NotBlank @Size(max = 2048) String url
) {
}
