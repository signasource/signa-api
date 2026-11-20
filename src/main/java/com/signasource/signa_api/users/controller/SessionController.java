package com.signasource.signa_api.users.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.users.dto.RecordSessionRequest;
import com.signasource.signa_api.users.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Records an app-usage session for the authenticated user (write-only telemetry). */
@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    public ResponseEntity<Void> record(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RecordSessionRequest request) {
        sessionService.record(userDetails.getUser(), request);
        return ResponseEntity.noContent().build();
    }
}
