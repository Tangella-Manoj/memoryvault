package io.memoryvault.controller;

import io.memoryvault.dto.ApiResponse;
import io.memoryvault.dto.vault.BulkImportRequest;
import io.memoryvault.dto.vault.ImportJobResponse;
import io.memoryvault.security.SecurityUtil;
import io.memoryvault.service.ImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vault/import")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/json")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ImportJobResponse> startImport(@Valid @RequestBody BulkImportRequest request) {
        Long userId = SecurityUtil.currentUserId();
        return ApiResponse.success(importService.startImport(userId, request), "Import started");
    }

    @GetMapping("/status/{jobId}")
    public ApiResponse<ImportJobResponse> status(@PathVariable Long jobId) {
        Long userId = SecurityUtil.currentUserId();
        return ApiResponse.success(importService.getStatus(userId, jobId), "Import status");
    }
}
