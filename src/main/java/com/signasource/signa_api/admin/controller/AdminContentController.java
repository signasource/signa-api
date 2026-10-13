package com.signasource.signa_api.admin.controller;

import com.signasource.signa_api.admin.dto.AdminCourseResponse;
import com.signasource.signa_api.admin.dto.AdminSignResponse;
import com.signasource.signa_api.admin.dto.AdminWaitlistEntryResponse;
import com.signasource.signa_api.admin.service.AdminService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminContentController {

    private final AdminService adminService;

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
}
