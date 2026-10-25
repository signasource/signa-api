package com.signasource.signa_api.admin.controller;

import com.signasource.signa_api.admin.dto.AdminCourseResponse;
import com.signasource.signa_api.admin.dto.AdminSignResponse;
import com.signasource.signa_api.admin.dto.AdminWaitlistEntryResponse;
import com.signasource.signa_api.admin.dto.ContentImportRunResponse;
import com.signasource.signa_api.admin.service.AdminService;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.content.entity.ContentImportRun;
import com.signasource.signa_api.content.entity.ContentImportTrigger;
import com.signasource.signa_api.content.service.ContentImportHistoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminContentController {

    private final AdminService adminService;
    private final ContentImportHistoryService contentImportHistoryService;

    @GetMapping("/courses")
    public ResponseEntity<List<AdminCourseResponse>> getCourses() {
        return ResponseEntity.ok(adminService.getCourses());
    }

    @GetMapping("/signs")
    public ResponseEntity<Page<AdminSignResponse>> getSigns(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(adminService.getSigns(query, pageable));
    }

    @GetMapping("/waitlist")
    public ResponseEntity<Page<AdminWaitlistEntryResponse>> getWaitlist(
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(adminService.getWaitlist(pageable));
    }

    @PostMapping("/content/import")
    public ResponseEntity<ContentImportRunResponse> importContent(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ContentImportRun run =
                contentImportHistoryService.run(
                        ContentImportTrigger.MANUAL, userDetails.getUser().getEmail());
        return ResponseEntity.ok(ContentImportRunResponse.from(run));
    }

    @GetMapping("/content/import-runs")
    public ResponseEntity<Page<ContentImportRunResponse>> getContentImportRuns(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                contentImportHistoryService.history(pageable).map(ContentImportRunResponse::from));
    }
}
