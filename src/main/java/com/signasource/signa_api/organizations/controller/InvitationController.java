package com.signasource.signa_api.organizations.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.organizations.dto.AcceptInvitationResponse;
import com.signasource.signa_api.organizations.dto.InvitationPreviewResponse;
import com.signasource.signa_api.organizations.service.SeatInvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final SeatInvitationService invitationService;

    @GetMapping("/{token}")
    public ResponseEntity<InvitationPreviewResponse> preview(@PathVariable String token) {
        return ResponseEntity.ok(invitationService.preview(token));
    }

    @PostMapping("/{token}/accept")
    public ResponseEntity<AcceptInvitationResponse> accept(
            @PathVariable String token, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(invitationService.accept(userDetails.getUser(), token));
    }
}
