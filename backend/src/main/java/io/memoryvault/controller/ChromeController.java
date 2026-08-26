package io.memoryvault.controller;

import io.memoryvault.dto.ApiResponse;
import io.memoryvault.dto.chrome.ChromeQuickSaveRequest;
import io.memoryvault.dto.chrome.ChromeSelectionSaveRequest;
import io.memoryvault.dto.vault.VaultItemResponse;
import io.memoryvault.security.SecurityUtil;
import io.memoryvault.service.ChromeSessionService;
import io.memoryvault.service.VaultItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/chrome")
public class ChromeController {

    private final ChromeSessionService chromeSessionService;
    private final VaultItemService vaultItemService;

    public ChromeController(ChromeSessionService chromeSessionService, VaultItemService vaultItemService) {
        this.chromeSessionService = chromeSessionService;
        this.vaultItemService = vaultItemService;
    }

    @PostMapping("/session")
    public ApiResponse<Map<String, String>> createSession() {
        Long userId = SecurityUtil.currentUserId();
        String token = chromeSessionService.createSession(userId);
        return ApiResponse.success(Map.of("chromeToken", token), "Chrome session created");
    }

    @PostMapping("/save/quick")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VaultItemResponse> saveQuick(@Valid @RequestBody ChromeQuickSaveRequest request) {
        Long userId = SecurityUtil.currentUserId();
        return ApiResponse.success(vaultItemService.saveFromChromeQuick(userId, request.url()), "Saved from extension");
    }

    @PostMapping("/save/selection")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VaultItemResponse> saveSelection(@Valid @RequestBody ChromeSelectionSaveRequest request) {
        Long userId = SecurityUtil.currentUserId();
        return ApiResponse.success(
                vaultItemService.saveFromChromeSelection(userId, request.url(), request.selectedText()),
                "Saved selection from extension"
        );
    }
}
