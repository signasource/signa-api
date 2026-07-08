package com.signasource.signa_api.learning.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.dto.CreateSignReportRequest;
import com.signasource.signa_api.learning.service.SignReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sign-reports")
@RequiredArgsConstructor
public class SignReportController {

    private final SignReportService signReportService;

    @PostMapping
    public ResponseEntity<Void> submitReport(
            @Valid @RequestBody CreateSignReportRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        signReportService.createReport(request, userDetails.getUser().getId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
