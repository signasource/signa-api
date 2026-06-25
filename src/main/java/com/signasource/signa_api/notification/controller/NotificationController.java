package com.signasource.signa_api.notification.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.notification.dto.NotificationPreferenceRequest;
import com.signasource.signa_api.notification.dto.NotificationPreferenceResponse;
import com.signasource.signa_api.notification.dto.NotificationResponse;
import com.signasource.signa_api.notification.dto.UnreadCountResponse;
import com.signasource.signa_api.notification.service.NotificationPreferenceService;
import com.signasource.signa_api.notification.service.NotificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;
	private final NotificationPreferenceService preferenceService;

	@GetMapping
	public ResponseEntity<Page<NotificationResponse>> getInbox(@AuthenticationPrincipal CustomUserDetails userDetails,
			Pageable pageable) {
		return ResponseEntity.ok(notificationService.getInbox(userDetails.getUser(), pageable));
	}

	@GetMapping("/unread")
	public ResponseEntity<List<NotificationResponse>> getUnread(
			@AuthenticationPrincipal CustomUserDetails userDetails) {
		return ResponseEntity.ok(notificationService.getUnread(userDetails.getUser()));
	}

	@GetMapping("/unread-count")
	public ResponseEntity<UnreadCountResponse> getUnreadCount(@AuthenticationPrincipal CustomUserDetails userDetails) {
		return ResponseEntity.ok(new UnreadCountResponse(notificationService.countUnread(userDetails.getUser())));
	}

	@PatchMapping("/{id}/read")
	public ResponseEntity<Void> markAsRead(@AuthenticationPrincipal CustomUserDetails userDetails,
			@PathVariable Long id) {
		notificationService.markAsRead(userDetails.getUser(), id);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/read-all")
	public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal CustomUserDetails userDetails) {
		notificationService.markAllAsRead(userDetails.getUser());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/preferences")
	public ResponseEntity<NotificationPreferenceResponse> getPreferences(
			@AuthenticationPrincipal CustomUserDetails userDetails) {
		return ResponseEntity.ok(preferenceService.getOrCreate(userDetails.getUser()));
	}

	@PutMapping("/preferences")
	public ResponseEntity<NotificationPreferenceResponse> updatePreferences(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@Valid @RequestBody NotificationPreferenceRequest request) {
		return ResponseEntity.ok(preferenceService.update(userDetails.getUser(), request));
	}
}
