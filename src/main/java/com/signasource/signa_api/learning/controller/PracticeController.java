package com.signasource.signa_api.learning.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.dto.LearnedSignResponse;
import com.signasource.signa_api.learning.dto.LessonBlockResponse;
import com.signasource.signa_api.learning.dto.PracticeAttemptRequest;
import com.signasource.signa_api.learning.dto.PracticeMistakeResponse;
import com.signasource.signa_api.learning.dto.PracticeMistakeReviewCompletedResponse;
import com.signasource.signa_api.learning.dto.PracticeSummaryResponse;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.service.PracticeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/practice")
@RequiredArgsConstructor
public class PracticeController {

    private static final int DEFAULT_LIMIT = 10;
    private static final int DEFAULT_MISTAKES_LIMIT = 20;

    private final PracticeService practiceService;

    @GetMapping("/summary")
    public ResponseEntity<PracticeSummaryResponse> getSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(practiceService.getSummary(userDetails.getUser()));
    }

    @GetMapping("/exercises")
    public ResponseEntity<List<LessonBlockResponse>> getExercisesByType(
            @RequestParam BlockType type,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                practiceService.getExercisesByType(userDetails.getUser(), type, limit));
    }

    @GetMapping("/signs")
    public ResponseEntity<List<LearnedSignResponse>> getLearnedSigns(
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(practiceService.getLearnedSigns(userDetails.getUser(), limit));
    }

    @GetMapping("/signs/{meaning}/exercises")
    public ResponseEntity<List<LessonBlockResponse>> getExercisesForSign(
            @PathVariable String meaning,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                practiceService.getExercisesForSign(userDetails.getUser(), meaning, limit));
    }

    @GetMapping("/mistakes")
    public ResponseEntity<List<PracticeMistakeResponse>> getMistakes(
            @RequestParam(defaultValue = "" + DEFAULT_MISTAKES_LIMIT) int limit,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(practiceService.getMistakes(userDetails.getUser(), limit));
    }

    @GetMapping("/mistakes/exercises")
    public ResponseEntity<List<LessonBlockResponse>> getMistakeExercises(
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(practiceService.getMistakeExercises(userDetails.getUser(), limit));
    }

    @PostMapping("/attempts")
    public ResponseEntity<Void> recordAttempt(
            @Valid @RequestBody PracticeAttemptRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        practiceService.recordAttempt(
                userDetails.getUser(), request.lessonBlockId(), request.isCorrect());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Unlike other practice modes, finishing a mistake-review batch grants real XP — it always
     * works off the user's actual pending mistakes, so the reward can't be farmed for free once
     * they're resolved (see PracticeService#completeMistakeReview).
     */
    @PostMapping("/mistakes/complete")
    public ResponseEntity<PracticeMistakeReviewCompletedResponse> completeMistakeReview(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        int xpEarned = practiceService.completeMistakeReview(userDetails.getUser());
        return ResponseEntity.ok(new PracticeMistakeReviewCompletedResponse(xpEarned));
    }
}
