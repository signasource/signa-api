package com.signasource.signa_api.gamification.controller;

import com.signasource.signa_api.gamification.dto.ShopItemResponse;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.service.ShopCatalogService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/items")
@RequiredArgsConstructor
public class ShopItemController {

    private final ShopCatalogService shopCatalogService;

    @GetMapping
    public ResponseEntity<List<ShopItemResponse>> getItems(
            @RequestParam(required = false) ShopItemType type) {
        return ResponseEntity.ok(shopCatalogService.getItems(type));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShopItemResponse> getItemById(@PathVariable UUID id) {
        return ResponseEntity.ok(shopCatalogService.getItemById(id));
    }
}
