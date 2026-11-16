package com.signasource.signa_api.admin.controller;

import com.signasource.signa_api.admin.dto.AdminPurchaseDetailResponse;
import com.signasource.signa_api.admin.dto.AdminPurchaseListItemResponse;
import com.signasource.signa_api.admin.service.AdminPurchasesService;
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
@RequestMapping("/admin/purchases")
@RequiredArgsConstructor
public class AdminPurchaseController {

    private final AdminPurchasesService service;

    @GetMapping
    public ResponseEntity<List<AdminPurchaseListItemResponse>> list() {
        return ResponseEntity.ok(service.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminPurchaseDetailResponse> detail(
            @PathVariable UUID id, @RequestParam String type) {
        return ResponseEntity.ok(service.detail(id, type));
    }
}
