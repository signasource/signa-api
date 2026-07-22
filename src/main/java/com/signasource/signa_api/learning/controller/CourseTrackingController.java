package com.signasource.signa_api.learning.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.dto.ExerciseAttemptRequest;
import com.signasource.signa_api.learning.dto.LessonCompletionRequest;
import com.signasource.signa_api.learning.dto.TopicStatusRequest;
import com.signasource.signa_api.learning.service.CourseTrackingService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/learning/tracking")
@RequiredArgsConstructor
public class CourseTrackingController {

    private final CourseTrackingService trackingService;

    @PostMapping("/courses/{courseVersionId}/enroll")
    public ResponseEntity<Void> enroll(
            @PathVariable UUID courseVersionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        trackingService.enrollUserInCourse(userDetails.getUser().getId(), courseVersionId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PatchMapping("/topics/{topicId}/status")
    public ResponseEntity<Void> updateTopicStatus(
            @PathVariable UUID topicId,
            @RequestBody TopicStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        trackingService.updateTopicStatus(userDetails.getUser().getId(), topicId, request.status());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/lessons/{lessonId}/complete")
    public ResponseEntity<Void> completeLesson(
            @PathVariable UUID lessonId,
            @RequestBody LessonCompletionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        trackingService.completeLesson(userDetails.getUser().getId(), lessonId, request.xpEarned());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/blocks/{lessonBlockId}/attempts")
    public ResponseEntity<Void> recordAttempt(
            @PathVariable UUID lessonBlockId,
            @RequestBody ExerciseAttemptRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        trackingService.recordExerciseAttempt(
                userDetails.getUser().getId(), lessonBlockId, request.isCorrect());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
