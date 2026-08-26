package io.memoryvault.controller;

import io.memoryvault.dto.ApiResponse;
import io.memoryvault.dto.vault.VaultItemResponse;
import io.memoryvault.exception.ApiException;
import io.memoryvault.security.SecurityUtil;
import io.memoryvault.service.UrlExtractor;
import io.memoryvault.service.VaultItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backend side of the PWA Web Share Target flow (Upgrade 2). The OS share sheet's raw POST
 * (title/text/url, form-encoded, no auth headers — the browser navigates there directly) is
 * intercepted client-side by the service worker and redirected to the React
 * {@code /share-target} route, which then calls this endpoint as a normal authenticated
 * fetch carrying the user's JWT — that's why this can require auth like every other
 * endpoint despite the native share POST itself having none.
 */
@RestController
@RequestMapping("/api/vault")
public class ShareTargetController {

    private final UrlExtractor urlExtractor;
    private final VaultItemService vaultItemService;

    public ShareTargetController(UrlExtractor urlExtractor, VaultItemService vaultItemService) {
        this.urlExtractor = urlExtractor;
        this.vaultItemService = vaultItemService;
    }

    @PostMapping(path = "/share-target", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VaultItemResponse> shareTarget(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String url
    ) {
        String bestUrl = urlExtractor.extractBestUrl(title, text, url);
        if (bestUrl == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_URL_FOUND",
                    "Could not find a URL in the shared title, text, or url");
        }

        Long userId = SecurityUtil.currentUserId();
        return ApiResponse.success(vaultItemService.saveFromShareTarget(userId, bestUrl), "Saved from share");
    }
}
