package com.signasource.signa_api.learning.controller;

import com.signasource.signa_api.learning.dto.CreateSignRequest;
import com.signasource.signa_api.learning.dto.SignAnimationResponse;
import com.signasource.signa_api.learning.dto.SignAnimationsRequest;
import com.signasource.signa_api.learning.dto.SignSummaryResponse;
import com.signasource.signa_api.learning.service.SignService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/signs")
@RequiredArgsConstructor
public class SignController {

    private final SignService signService;

    @GetMapping
    public ResponseEntity<Page<SignSummaryResponse>> getSigns(
            @RequestParam @NonNull UUID signLanguageId,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, page = 0) Pageable pageable) {

        return ResponseEntity.ok(signService.getSignsCatalog(signLanguageId, query, pageable));
    }

    @PostMapping
    public ResponseEntity<SignSummaryResponse> createSign(
            @Valid @RequestBody CreateSignRequest request) {
        SignSummaryResponse response = signService.createSign(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{meaning}/animation")
    public ResponseEntity<SignAnimationResponse> getSignAnimation(@PathVariable String meaning) {
        return ResponseEntity.ok(signService.getSignAnimation(meaning));
    }

    /**
     * Batched counterpart of {@link #getSignAnimation}, used by the lesson
     * player to fetch every animation URL a lesson needs in one request
     * instead of one per sign.
     */
    @PostMapping("/animations")
    public ResponseEntity<Map<String, String>> getSignAnimations(
            @Valid @RequestBody SignAnimationsRequest request) {
        return ResponseEntity.ok(signService.getSignAnimations(request.meanings()));
    }
}
