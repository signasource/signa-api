package com.signasource.signa_api.users.controller;

import com.signasource.signa_api.auth.dto.AuthResponse;
import com.signasource.signa_api.auth.dto.ChangePasswordRequest;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.auth.service.AuthService;
import com.signasource.signa_api.gamification.dto.DailyXpResponse;
import com.signasource.signa_api.gamification.dto.UserStatsResponse;
import com.signasource.signa_api.gamification.service.UserStatsService;
import com.signasource.signa_api.users.dto.DailyGoalResponse;
import com.signasource.signa_api.users.dto.PublicUserProfileResponse;
import com.signasource.signa_api.users.dto.UpdateDailyGoalRequest;
import com.signasource.signa_api.users.dto.UpdateUserSettingsRequest;
import com.signasource.signa_api.users.dto.UpdateUsernameRequest;
import com.signasource.signa_api.users.dto.UserProfileResponse;
import com.signasource.signa_api.users.dto.UserSearchResultResponse;
import com.signasource.signa_api.users.dto.UserSettingsResponse;
import com.signasource.signa_api.users.dto.UsernameAvailabilityResponse;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.service.PublicProfileService;
import com.signasource.signa_api.users.service.UserService;
import com.signasource.signa_api.users.service.UserSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final AuthService authService;
    private final UserSettingsService userSettingsService;
    private final PublicProfileService publicProfileService;
    private final UserService userService;
    private final UserStatsService userStatsService;

    @PutMapping("/password")
    public ResponseEntity<AuthResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        AuthResponse response = authService.changePassword(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/settings")
    public ResponseEntity<UserSettingsResponse> getSettings(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userSettingsService.getSettings(userDetails.getUser()));
    }

    @PatchMapping("/settings")
    public ResponseEntity<UserSettingsResponse> updateSettings(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateUserSettingsRequest request) {
        return ResponseEntity.ok(
                userSettingsService.updateSettings(userDetails.getUser(), request));
    }

    @PostMapping("/daily-goal")
    public ResponseEntity<DailyGoalResponse> setDailyGoal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateDailyGoalRequest request) {
        return ResponseEntity.status(201)
                .body(userSettingsService.setDailyGoal(userDetails.getUser(), request));
    }

    @GetMapping("/daily-goal")
    public ResponseEntity<DailyGoalResponse> getDailyGoal(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userSettingsService.getDailyGoal(userDetails.getUser()));
    }

    @PatchMapping("/daily-goal")
    public ResponseEntity<DailyGoalResponse> updateDailyGoal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateDailyGoalRequest request) {
        return ResponseEntity.ok(
                userSettingsService.updateDailyGoal(userDetails.getUser(), request));
    }

    @GetMapping("/username-availability")
    public ResponseEntity<UsernameAvailabilityResponse> checkUsernameAvailability(
            @RequestParam @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[a-zA-Z0-9_]+$") String username) {
        return ResponseEntity.ok(userService.checkUsernameAvailability(username));
    }

    @PatchMapping("/me")
    public ResponseEntity<Void> updateUsername(
            @Valid @RequestBody UpdateUsernameRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.updateUsername(request, userDetails.getUser());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMe(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(UserProfileResponse.from(userDetails.getUser()));
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResultResponse>> searchUsers(
            @RequestParam @NotBlank @Size(min = 2, max = 50) String query,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        int capped = Math.min(Math.max(limit, 1), 50);
        return ResponseEntity.ok(userService.searchUsers(userDetails.getUser(), query, capped));
    }

    @GetMapping("/{username}")
    public ResponseEntity<PublicUserProfileResponse> getByUsername(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String username,
            @AuthenticationPrincipal(errorOnInvalidType = false) CustomUserDetails userDetails) {
        User requester = userDetails != null ? userDetails.getUser() : null;
        return ResponseEntity.ok(publicProfileService.getByUsername(username, requester));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.deleteAccount(userDetails.getUser());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/stats")
    public ResponseEntity<UserStatsResponse> getMeStats(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userStatsService.getStats(userDetails.getUser()));
    }

    @GetMapping("/me/weekly-xp")
    public ResponseEntity<List<DailyXpResponse>> getWeeklyXpBreakdown(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userStatsService.getWeeklyXpBreakdown(userDetails.getUser()));
    }
}
