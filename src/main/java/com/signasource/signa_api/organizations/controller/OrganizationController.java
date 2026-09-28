package com.signasource.signa_api.organizations.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.organizations.dto.CreateInviteCodeRequest;
import com.signasource.signa_api.organizations.dto.CreateOrganizationRequest;
import com.signasource.signa_api.organizations.dto.InviteCodeResponse;
import com.signasource.signa_api.organizations.dto.OrganizationResponse;
import com.signasource.signa_api.organizations.dto.RedeemInviteCodeRequest;
import com.signasource.signa_api.organizations.dto.RedeemInviteCodeResponse;
import com.signasource.signa_api.organizations.service.InviteCodeService;
import com.signasource.signa_api.organizations.service.OrganizationService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final InviteCodeService inviteCodeService;

    @PostMapping
    public ResponseEntity<OrganizationResponse> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.createOrganization(request));
    }

    @PostMapping("/{organizationId}/invite-codes")
    public ResponseEntity<InviteCodeResponse> createInviteCode(
            @PathVariable UUID organizationId, @Valid @RequestBody CreateInviteCodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inviteCodeService.createInviteCode(organizationId, request));
    }

    @GetMapping("/{organizationId}/invite-codes")
    public ResponseEntity<List<InviteCodeResponse>> getInviteCodes(
            @PathVariable UUID organizationId) {
        return ResponseEntity.ok(inviteCodeService.getOrganizationInviteCodes(organizationId));
    }

    @PostMapping("/invite-codes/redeem")
    public ResponseEntity<RedeemInviteCodeResponse> redeemInviteCode(
            @Valid @RequestBody RedeemInviteCodeRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                inviteCodeService.redeem(userDetails.getUser(), request.code()));
    }
}
