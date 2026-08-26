package io.memoryvault.dto.vault;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkImportRequest(
        @NotEmpty @Size(max = 500) List<@jakarta.validation.constraints.NotBlank String> urls
) {
}
