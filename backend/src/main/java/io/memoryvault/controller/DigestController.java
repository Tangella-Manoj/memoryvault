package io.memoryvault.controller;

import io.memoryvault.dto.ApiResponse;
import io.memoryvault.dto.vault.DigestResponse;
import io.memoryvault.security.SecurityUtil;
import io.memoryvault.service.DigestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vault/digest")
public class DigestController {

    private final DigestService digestService;

    public DigestController(DigestService digestService) {
        this.digestService = digestService;
    }

    @GetMapping("/today")
    public ApiResponse<DigestResponse> today() {
        Long userId = SecurityUtil.currentUserId();
        return ApiResponse.success(digestService.getOrGenerateToday(userId), "Today's digest");
    }
}
