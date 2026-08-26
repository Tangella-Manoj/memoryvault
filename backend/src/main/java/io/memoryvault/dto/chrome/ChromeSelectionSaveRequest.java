package io.memoryvault.dto.chrome;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChromeSelectionSaveRequest(
        @NotBlank @Size(max = 2048) String url,
        @NotBlank @Size(max = 5000) String selectedText
) {
}
