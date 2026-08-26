package io.memoryvault.controller;

import io.memoryvault.dto.ApiResponse;
import io.memoryvault.dto.chrome.ChromeQuickSaveRequest;
import io.memoryvault.dto.chrome.ChromeSearchResult;
import io.memoryvault.dto.chrome.ChromeSelectionSaveRequest;
import io.memoryvault.dto.vault.VaultItemResponse;
import io.memoryvault.exception.ApiException;
import io.memoryvault.security.SecurityUtil;
import io.memoryvault.service.ChromeSessionService;
import io.memoryvault.service.VaultItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
        return ApiResponse.success(
                vaultItemService.saveFromChromeQuick(userId, request.url(), request.source()),
                "Saved from extension"
        );
    }

    /**
     * Contextual search for the extension's search-engine overlay (Upgrade 1). Same
     * ranking as {@code GET /api/vault/search}, formatted flat/snake_case for the
     * content script and capped at 5 results.
     */
    @GetMapping("/search")
    public ApiResponse<List<ChromeSearchResult>> search(@RequestParam("q") String query) {
        if (query == null || query.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BLANK_QUERY", "q must not be empty");
        }
        Long userId = SecurityUtil.currentUserId();
        return ApiResponse.success(vaultItemService.chromeSearch(userId, query), "Chrome overlay search results");
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
