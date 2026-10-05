package com.signasource.signa_api.learning.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.dto.LessonDetailResponse;
import com.signasource.signa_api.learning.service.LessonService;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LessonController {

    private final LessonService lessonService;

    @GetMapping("/{id}")
    public ResponseEntity<LessonDetailResponse> getLessonContent(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @NonNull @PathVariable UUID id) {
        return ResponseEntity.ok(lessonService.getLessonContent(userDetails.getUser(), id));
    }
}
