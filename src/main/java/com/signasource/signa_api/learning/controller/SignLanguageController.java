package com.signasource.signa_api.learning.controller;

import com.signasource.signa_api.learning.dto.SignLanguageResponse;
import com.signasource.signa_api.learning.service.SignLanguageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sign-languages")
@RequiredArgsConstructor
public class SignLanguageController {

    private final SignLanguageService signLanguageService;

    @GetMapping
    public ResponseEntity<List<SignLanguageResponse>> getSignLanguages() {
        return ResponseEntity.ok(signLanguageService.getAll());
    }
}
