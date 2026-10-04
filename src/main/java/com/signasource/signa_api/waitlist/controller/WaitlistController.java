package com.signasource.signa_api.waitlist.controller;

import com.signasource.signa_api.waitlist.dto.WaitlistRequest;
import com.signasource.signa_api.waitlist.service.WaitlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PostMapping
    public ResponseEntity<Void> subscribe(@Valid @RequestBody WaitlistRequest request) {
        waitlistService.subscribe(request.email());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
