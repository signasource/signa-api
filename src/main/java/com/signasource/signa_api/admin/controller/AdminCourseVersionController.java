package com.signasource.signa_api.admin.controller;

import com.signasource.signa_api.admin.dto.AdminCourseVersionDetailResponse;
import com.signasource.signa_api.admin.dto.AdminCourseVersionListItemResponse;
import com.signasource.signa_api.admin.dto.CreateCourseRequest;
import com.signasource.signa_api.admin.dto.SaveCourseVersionRequest;
import com.signasource.signa_api.admin.service.AdminCourseVersionService;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/course-versions")
@RequiredArgsConstructor
public class AdminCourseVersionController {

    private final AdminCourseVersionService service;

    @GetMapping
    public ResponseEntity<List<AdminCourseVersionListItemResponse>> listVersions() {
        return ResponseEntity.ok(service.listVersions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminCourseVersionDetailResponse> getVersion(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getVersion(id));
    }

    @PostMapping
    public ResponseEntity<AdminCourseVersionDetailResponse> createCourse(
            @Valid @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AdminCourseVersionDetailResponse response =
                service.createCourse(request, userDetails.getUser().getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/fork")
    public ResponseEntity<AdminCourseVersionDetailResponse> forkVersion(
            @PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        AdminCourseVersionDetailResponse response =
                service.forkVersion(id, userDetails.getUser().getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminCourseVersionDetailResponse> saveVersion(
            @PathVariable UUID id,
            @Valid @RequestBody SaveCourseVersionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                service.saveVersion(id, request, userDetails.getUser().getEmail()));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<AdminCourseVersionDetailResponse> publishVersion(@PathVariable UUID id) {
        return ResponseEntity.ok(service.publishVersion(id));
    }
}
