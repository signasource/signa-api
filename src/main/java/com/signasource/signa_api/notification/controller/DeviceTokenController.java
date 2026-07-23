package com.signasource.signa_api.notification.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.notification.dto.DeviceTokenRequest;
import com.signasource.signa_api.notification.service.DeviceTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/device-tokens")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @PostMapping
    public ResponseEntity<Void> registerDeviceToken(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DeviceTokenRequest request) {
        deviceTokenService.registerToken(
                userDetails.getUser(), request.token(), request.platform());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping
    public ResponseEntity<Void> removeDeviceToken(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DeviceTokenRequest request) {
        deviceTokenService.removeToken(userDetails.getUser(), request.token());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/all")
    public ResponseEntity<Void> removeAllDeviceTokens(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        deviceTokenService.removeAllTokensForUser(userDetails.getUser());
        return ResponseEntity.noContent().build();
    }
}
