package com.signasource.signa_api.organizations.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.organizations.service.OrganizationMemberService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/organizations/{organizationId}/members")
@RequiredArgsConstructor
public class OrganizationMemberController {

    private final OrganizationMemberService memberService;

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID organizationId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        memberService.removeMember(userDetails.getUser(), organizationId, userId);
        return ResponseEntity.noContent().build();
    }
}
