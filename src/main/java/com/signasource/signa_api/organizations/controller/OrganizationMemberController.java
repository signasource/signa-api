package com.signasource.signa_api.organizations.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.organizations.dto.MemberActivityStatus;
import com.signasource.signa_api.organizations.dto.MemberProgressResponse;
import com.signasource.signa_api.organizations.dto.OrganizationMemberSummaryResponse;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.service.MetricsPeriod;
import com.signasource.signa_api.organizations.service.OrganizationMemberQueryService;
import com.signasource.signa_api.organizations.service.OrganizationMemberService;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/organizations/{organizationId}/members")
@RequiredArgsConstructor
public class OrganizationMemberController {

    private final OrganizationMemberService memberService;
    private final OrganizationMemberQueryService memberQueryService;

    @GetMapping
    public ResponseEntity<Page<OrganizationMemberSummaryResponse>> getMembers(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "ACTIVE") MemberStatus status,
            @RequestParam(required = false) MemberActivityStatus activity,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @PageableDefault(size = 20, sort = "name") Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                memberQueryService.getMembers(
                        userDetails.getUser(),
                        organizationId,
                        query,
                        status,
                        activity,
                        courseId,
                        MetricsPeriod.of(from, to),
                        pageable));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<MemberProgressResponse> getMemberProgress(
            @PathVariable UUID organizationId,
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                memberQueryService.getMemberProgress(
                        userDetails.getUser(), organizationId, userId, MetricsPeriod.of(from, to)));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID organizationId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        memberService.removeMember(userDetails.getUser(), organizationId, userId);
        return ResponseEntity.noContent().build();
    }
}
