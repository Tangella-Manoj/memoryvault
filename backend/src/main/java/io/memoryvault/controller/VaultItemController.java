package io.memoryvault.controller;

import io.memoryvault.dto.ApiResponse;
import io.memoryvault.dto.vault.SaveVaultItemRequest;
import io.memoryvault.dto.vault.SearchResultItem;
import io.memoryvault.dto.vault.VaultItemResponse;
import io.memoryvault.security.SecurityUtil;
import io.memoryvault.service.VaultItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/search")
    public ApiResponse<List<SearchResultItem>> search(@RequestParam("q") String query) {
        Long userId = SecurityUtil.currentUserId();
        return ApiResponse.success(vaultItemService.search(userId, query), "Search results");
    }

    @GetMapping("/resurface")
    public ApiResponse<List<SearchResultItem>> resurface(@RequestParam(defaultValue = "10") int limit) {
        Long userId = SecurityUtil.currentUserId();
        return ApiResponse.success(vaultItemService.resurfaceFeed(userId, limit), "Resurfacing feed");
    }
}
