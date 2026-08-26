package io.memoryvault.controller;

import io.memoryvault.dto.ApiResponse;
import io.memoryvault.dto.IntegrationStatusResponse;
import io.memoryvault.domain.User;
import io.memoryvault.domain.UserIntegration;
import io.memoryvault.domain.enums.IntegrationPlatform;
import io.memoryvault.domain.enums.ItemSource;
import io.memoryvault.exception.ApiException;
import io.memoryvault.repository.UserIntegrationRepository;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.repository.VaultItemRepository;
import io.memoryvault.security.SecurityUtil;
import io.memoryvault.service.TokenCipher;
import io.memoryvault.service.youtube.GoogleOAuthClient;
import io.memoryvault.service.youtube.OAuthStateStore;
import io.memoryvault.service.youtube.YouTubeSyncService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * YouTube OAuth connect/callback/sync flow (Upgrade 4). {@code /connect} is called by an
 * authenticated SPA request; {@code /callback} is a plain browser GET from Google with no
 * auth header — {@link OAuthStateStore} is what recovers the initiating user's identity.
 */
@RestController
@RequestMapping("/api/integrations/youtube")
public class YouTubeIntegrationController {

    private final GoogleOAuthClient oAuthClient;
    private final OAuthStateStore stateStore;
    private final UserIntegrationRepository userIntegrationRepository;
    private final UserRepository userRepository;
    private final VaultItemRepository vaultItemRepository;
    private final TokenCipher tokenCipher;
    private final YouTubeSyncService youTubeSyncService;
    private final String frontendBaseUrl;

    public YouTubeIntegrationController(
            GoogleOAuthClient oAuthClient,
            OAuthStateStore stateStore,
            UserIntegrationRepository userIntegrationRepository,
            UserRepository userRepository,
            VaultItemRepository vaultItemRepository,
            TokenCipher tokenCipher,
            YouTubeSyncService youTubeSyncService,
            @Value("${app.frontend-base-url}") String frontendBaseUrl
    ) {
        this.oAuthClient = oAuthClient;
        this.stateStore = stateStore;
        this.userIntegrationRepository = userIntegrationRepository;
        this.userRepository = userRepository;
        this.vaultItemRepository = vaultItemRepository;
        this.tokenCipher = tokenCipher;
        this.youTubeSyncService = youTubeSyncService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @GetMapping("/connect")
    public ApiResponse<Map<String, String>> connect() {
        if (!oAuthClient.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "YOUTUBE_NOT_CONFIGURED",
                    "GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET are not configured on this server");
        }
        Long userId = SecurityUtil.currentUserId();
        String state = stateStore.create(userId);
        return ApiResponse.success(Map.of("authorizationUrl", oAuthClient.buildAuthorizationUrl(state)), "Authorization URL");
    }

    @GetMapping("/callback")
    public void callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletResponse response
    ) throws IOException {
        if (error != null || code == null || state == null) {
            response.sendRedirect(frontendBaseUrl + "/profile?youtube=error");
            return;
        }

        Long userId = stateStore.consume(state).orElse(null);
        if (userId == null) {
            response.sendRedirect(frontendBaseUrl + "/profile?youtube=error");
            return;
        }

        try {
            GoogleOAuthClient.TokenResult tokens = oAuthClient.exchangeCode(code);
            saveIntegration(userId, tokens);
            response.sendRedirect(frontendBaseUrl + "/profile?youtube=connected");
        } catch (Exception ex) {
            response.sendRedirect(frontendBaseUrl + "/profile?youtube=error");
        }
    }

    @org.springframework.transaction.annotation.Transactional
    void saveIntegration(Long userId, GoogleOAuthClient.TokenResult tokens) {
        User user = userRepository.getReferenceById(userId);
        UserIntegration integration = userIntegrationRepository.findByUserIdAndPlatform(userId, IntegrationPlatform.YOUTUBE)
                .orElseGet(() -> UserIntegration.builder().user(user).platform(IntegrationPlatform.YOUTUBE).build());

        integration.setAccessToken(tokenCipher.encrypt(tokens.accessToken()));
        if (tokens.refreshToken() != null) {
            integration.setRefreshToken(tokenCipher.encrypt(tokens.refreshToken()));
        }
        integration.setTokenExpiresAt(tokens.expiresAt());
        integration.setSyncEnabled(true);
        userIntegrationRepository.save(integration);
    }

    @PostMapping("/sync")
    public ApiResponse<Map<String, Integer>> sync() {
        Long userId = SecurityUtil.currentUserId();
        try {
            int created = youTubeSyncService.syncUser(userId);
            return ApiResponse.success(Map.of("itemsCreated", created), "Sync complete");
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "SYNC_FAILED", ex.getMessage());
        }
    }

    @GetMapping("/status")
    public ApiResponse<IntegrationStatusResponse> status() {
        Long userId = SecurityUtil.currentUserId();
        var integration = userIntegrationRepository.findByUserIdAndPlatform(userId, IntegrationPlatform.YOUTUBE);

        if (integration.isEmpty()) {
            return ApiResponse.success(new IntegrationStatusResponse(false, false, null, 0), "Not connected");
        }

        long totalSynced = vaultItemRepository.countByUserIdAndSource(userId, ItemSource.YOUTUBE);
        var i = integration.get();
        return ApiResponse.success(
                new IntegrationStatusResponse(true, i.isSyncEnabled(), i.getLastSyncedAt(), totalSynced),
                "Connected"
        );
    }

    @PostMapping("/toggle")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<IntegrationStatusResponse> toggle(@RequestParam boolean enabled) {
        Long userId = SecurityUtil.currentUserId();
        UserIntegration integration = userIntegrationRepository.findByUserIdAndPlatform(userId, IntegrationPlatform.YOUTUBE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_CONNECTED", "YouTube is not connected"));
        integration.setSyncEnabled(enabled);
        userIntegrationRepository.save(integration);

        long totalSynced = vaultItemRepository.countByUserIdAndSource(userId, ItemSource.YOUTUBE);
        return ApiResponse.success(
                new IntegrationStatusResponse(true, enabled, integration.getLastSyncedAt(), totalSynced),
                "Updated"
        );
    }
}
