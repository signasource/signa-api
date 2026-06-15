package com.signasource.signa_api.learning.controller;

import java.util.UUID;

import lombok.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.signasource.signa_api.learning.dto.CourseDetailResponse;
import com.signasource.signa_api.learning.dto.CourseSummaryResponse;
import com.signasource.signa_api.learning.service.CourseService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {
	private final CourseService courseService;

	@GetMapping
	public ResponseEntity<Page<CourseSummaryResponse>> getCoursesCatalog(@RequestParam UUID signLanguageId,
			@PageableDefault(size = 10, page = 0) Pageable pageable) {

		return ResponseEntity.ok(courseService.getCoursesCatalog(signLanguageId, pageable));
	}

	@GetMapping("/{id}")
	public ResponseEntity<CourseDetailResponse> getCourseDetail(@NonNull @PathVariable UUID id) {
		return ResponseEntity.ok(courseService.getCourseDetail(id));
	}
}
