package com.signasource.signa_api.learning.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.dto.LessonBlockInteractionRequest;
import com.signasource.signa_api.learning.service.CourseTrackingService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/learning/tracking")
@RequiredArgsConstructor
@Validated
public class CourseTrackingController {

    private final CourseTrackingService trackingService;

    @PostMapping("/courses/{courseVersionId}/enroll")
    public ResponseEntity<Void> enroll(
            @PathVariable UUID courseVersionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        trackingService.enrollUserInCourse(userDetails.getUser(), courseVersionId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/blocks/{lessonBlockId}/interactions")
    public ResponseEntity<Void> recordInteraction(
            @PathVariable UUID lessonBlockId,
            @Valid @RequestBody LessonBlockInteractionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        trackingService.recordBlockInteraction(
                userDetails.getUser(), lessonBlockId, request.isCorrect());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
