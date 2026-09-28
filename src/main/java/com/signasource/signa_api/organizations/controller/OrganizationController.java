package com.signasource.signa_api.organizations.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.organizations.dto.AddOrganizationAdminRequest;
import com.signasource.signa_api.organizations.dto.AddOrganizationCourseRequest;
import com.signasource.signa_api.organizations.dto.CreateInviteCodeRequest;
import com.signasource.signa_api.organizations.dto.CreateOrganizationRequest;
import com.signasource.signa_api.organizations.dto.InviteByEmailRequest;
import com.signasource.signa_api.organizations.dto.InviteCodeResponse;
import com.signasource.signa_api.organizations.dto.MyOrganizationResponse;
import com.signasource.signa_api.organizations.dto.OrganizationCourseResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @GetMapping("/me")
    public ResponseEntity<MyOrganizationResponse> getMyOrganization(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(organizationService.getMyOrganization(userDetails.getUser()));
    }

    @PostMapping("/{organizationId}/admins")
    public ResponseEntity<Void> addAdmin(
            @PathVariable UUID organizationId,
            @Valid @RequestBody AddOrganizationAdminRequest request) {
        organizationService.addAdmin(organizationId, request.email());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{organizationId}/courses")
    public ResponseEntity<List<OrganizationCourseResponse>> getCourses(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                organizationService.getCourses(userDetails.getUser(), organizationId));
    }

    @PostMapping("/{organizationId}/courses")
    public ResponseEntity<OrganizationCourseResponse> addCourse(
            @PathVariable UUID organizationId,
            @Valid @RequestBody AddOrganizationCourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.addCourse(organizationId, request.courseId()));
    }

    @DeleteMapping("/{organizationId}/courses/{courseId}")
    public ResponseEntity<Void> removeCourse(
            @PathVariable UUID organizationId, @PathVariable UUID courseId) {
        organizationService.removeCourse(organizationId, courseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{organizationId}/invite-codes")
    public ResponseEntity<InviteCodeResponse> createInviteCode(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateInviteCodeRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        inviteCodeService.createInviteCode(
                                userDetails.getUser(), organizationId, request));
    }

    @GetMapping("/{organizationId}/invite-codes")
    public ResponseEntity<List<InviteCodeResponse>> getInviteCodes(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                inviteCodeService.getOrganizationInviteCodes(
                        userDetails.getUser(), organizationId));
    }

    @DeleteMapping("/{organizationId}/invite-codes/{inviteCodeId}")
    public ResponseEntity<Void> deactivateInviteCode(
            @PathVariable UUID organizationId,
            @PathVariable UUID inviteCodeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        inviteCodeService.deactivateInviteCode(userDetails.getUser(), organizationId, inviteCodeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{organizationId}/invitations")
    public ResponseEntity<InviteCodeResponse> inviteByEmail(
            @PathVariable UUID organizationId,
            @Valid @RequestBody InviteByEmailRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        inviteCodeService.inviteByEmail(
                                userDetails.getUser(), organizationId, request));
    }

    @PostMapping("/invite-codes/redeem")
    public ResponseEntity<RedeemInviteCodeResponse> redeemInviteCode(
            @Valid @RequestBody RedeemInviteCodeRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(inviteCodeService.redeem(userDetails.getUser(), request.code()));
    }
}
