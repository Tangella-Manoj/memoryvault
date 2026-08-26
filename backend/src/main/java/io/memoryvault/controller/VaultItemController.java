package io.memoryvault.controller;

import io.memoryvault.dto.ApiResponse;
import io.memoryvault.dto.vault.SaveVaultItemRequest;
import io.memoryvault.dto.vault.VaultItemResponse;
import io.memoryvault.security.SecurityUtil;
import io.memoryvault.service.VaultItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vault")
public class VaultItemController {

    private final VaultItemService vaultItemService;

    public VaultItemController(VaultItemService vaultItemService) {
        this.vaultItemService = vaultItemService;
    }

    @PostMapping("/save")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VaultItemResponse> save(@Valid @RequestBody SaveVaultItemRequest request) {
        Long userId = SecurityUtil.currentUserId();
        return ApiResponse.success(vaultItemService.save(userId, request), "Item saved");
    }

    @GetMapping("/{id}")
    public ApiResponse<VaultItemResponse> getById(@PathVariable Long id) {
        Long userId = SecurityUtil.currentUserId();
        return ApiResponse.success(vaultItemService.getById(userId, id), "Item retrieved");
    }
}
