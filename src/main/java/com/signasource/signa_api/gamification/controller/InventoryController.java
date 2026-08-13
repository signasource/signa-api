package com.signasource.signa_api.gamification.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.PurchaseRequest;
import com.signasource.signa_api.gamification.dto.PurchaseResponse;
import com.signasource.signa_api.gamification.dto.ShopItemResponse;
import com.signasource.signa_api.gamification.dto.UserInventoryResponse;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.service.InventoryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventories")
@RequiredArgsConstructor
@Validated
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/me")
    public ResponseEntity<UserInventoryResponse> getMyInventory(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(inventoryService.getInventory(userDetails.getUser()));
    }

    @GetMapping("/shop")
    public ResponseEntity<List<ShopItemResponse>> getShop(
            @RequestParam(required = false) ShopItemType itemType,
            @RequestParam(required = false) Integer maxPrice) {
        return ResponseEntity.ok(inventoryService.getShop(itemType, maxPrice));
    }

    @PostMapping("/shop/purchase")
    public ResponseEntity<PurchaseResponse> purchase(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PurchaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.purchase(userDetails.getUser(), request));
    }
}
