package com.signasource.signa_api.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.signasource.signa_api.notification.dto.BroadcastRequest;
import com.signasource.signa_api.notification.service.NotificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
public class NotificationAdminController {

	private final NotificationService notificationService;

	@PostMapping("/broadcast")
	public ResponseEntity<Void> broadcast(@Valid @RequestBody BroadcastRequest request) {
		notificationService.broadcastGlobal(request.code(), request.title(), request.body());
		return ResponseEntity.accepted().build();
	}
}
