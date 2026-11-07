package com.signasource.signa_api.learning.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.dto.CourseDetailResponse;
import com.signasource.signa_api.learning.dto.CourseSummaryResponse;
import com.signasource.signa_api.learning.service.CourseService;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {
    private final CourseService courseService;

    @GetMapping
    public ResponseEntity<Page<CourseSummaryResponse>> getCoursesCatalog(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam UUID signLanguageId,
            @PageableDefault(size = 10, page = 0) Pageable pageable) {

        return ResponseEntity.ok(
                courseService.getCoursesCatalog(userDetails.getUser(), signLanguageId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseDetailResponse> getCourseDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @NonNull @PathVariable UUID id) {
        return ResponseEntity.ok(courseService.getCourseDetail(userDetails.getUser(), id));
    }
}
