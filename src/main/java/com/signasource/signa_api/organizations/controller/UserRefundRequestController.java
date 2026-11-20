package com.signasource.signa_api.organizations.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.organizations.dto.CreateRefundRequestRequest;
import com.signasource.signa_api.organizations.dto.UserRefundRequestResponse;
import com.signasource.signa_api.organizations.service.UserRefundRequestService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserRefundRequestController {

    private final UserRefundRequestService service;

    @GetMapping("/store/refund-requests")
    public ResponseEntity<List<UserRefundRequestResponse>> list(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(service.listForUser(userDetails.getUser()));
    }

    @PostMapping("/organizations/{orgId}/purchases/{purchaseId}/refund-request")
    public ResponseEntity<UserRefundRequestResponse> createForSeats(
            @PathVariable UUID orgId,
            @PathVariable UUID purchaseId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateRefundRequestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createForSeats(orgId, purchaseId, userDetails.getUser(), request));
    }
}
