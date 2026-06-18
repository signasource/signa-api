package com.signasource.signa_api.learning.controller;

import java.util.UUID;

import lombok.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.signasource.signa_api.learning.dto.LessonDetailResponse;
import com.signasource.signa_api.learning.service.LessonService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LessonController {

	private final LessonService lessonService;

	@GetMapping("/{id}")
	public ResponseEntity<LessonDetailResponse> getLessonContent(@NonNull @PathVariable UUID id) {
		return ResponseEntity.ok(lessonService.getLessonContent(id));
	}
}
