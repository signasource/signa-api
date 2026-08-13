package com.signasource.signa_api.auth.controller;

import com.signasource.signa_api.auth.dto.AuthResponse;
import com.signasource.signa_api.auth.dto.ForgotPasswordRequest;
import com.signasource.signa_api.auth.dto.GoogleAuthRequest;
import com.signasource.signa_api.auth.dto.LoginRequest;
import com.signasource.signa_api.auth.dto.RefreshTokenRequest;
import com.signasource.signa_api.auth.dto.RegisterRequest;
import com.signasource.signa_api.auth.dto.ResendVerificationEmailRequest;
import com.signasource.signa_api.auth.dto.ResetPasswordRequest;
import com.signasource.signa_api.auth.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> authenticateWithGoogle(
            @Valid @RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(
                authService.authenticateWithGoogle(request.idToken(), request.dailyGoalMinutes()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @GetMapping("/verify")
    public ResponseEntity<Void> verifyEmail(
            @RequestParam @NotBlank @Size(min = 10, max = 100) String token) {
        authService.verifyAccount(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request, @RequestParam String token) {
        authService.resetPassword(request, token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-verification-email")
    public ResponseEntity<Void> resendVerificationEmail(
            @Valid @RequestBody ResendVerificationEmailRequest request) {
        authService.resendVerificationEmail(request);
        return ResponseEntity.ok().build();
    }
}
