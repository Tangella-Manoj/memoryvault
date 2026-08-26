package io.memoryvault.controller;

import io.memoryvault.dto.ApiResponse;
import io.memoryvault.dto.vault.AnalyticsResponse;
import io.memoryvault.security.SecurityUtil;
import io.memoryvault.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vault/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public ApiResponse<AnalyticsResponse> analytics() {
        Long userId = SecurityUtil.currentUserId();
        return ApiResponse.success(analyticsService.forUser(userId), "Analytics");
    }
}
