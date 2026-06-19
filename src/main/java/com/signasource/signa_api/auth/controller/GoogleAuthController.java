package com.signasource.signa_api.auth.controller;

import com.signasource.signa_api.auth.dto.AuthResponse;
import com.signasource.signa_api.auth.dto.GoogleAuthRequest;
import com.signasource.signa_api.auth.service.GoogleAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class GoogleAuthController {

	private final GoogleAuthService googleAuthService;

	@PostMapping("/google")
	public ResponseEntity<AuthResponse> authenticateWithGoogle(@Valid @RequestBody GoogleAuthRequest request) {
		AuthResponse authResponse = googleAuthService.authenticateWithGoogle(request.idToken());
		return ResponseEntity.ok(authResponse);
	}
}
