package com.signasource.signa_api.notification.event;

import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.signasource.signa_api.notification.service.DeviceTokenService;
import com.signasource.signa_api.notification.service.FcmResult;
import com.signasource.signa_api.notification.service.FirebaseService;

import lombok.RequiredArgsConstructor;

/**
 * Sends the actual push after the persisting transaction commits, on a separate
 * thread. This keeps the FCM network call out of the database transaction and
 * guarantees we never push for a notification that rolled back. Invalid tokens
 * reported by FCM are purged.
 */
@Component
@RequiredArgsConstructor
public class NotificationPushListener {

	private final DeviceTokenService deviceTokenService;
	private final FirebaseService firebaseService;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onNotificationPersisted(NotificationPersistedEvent event) {
		List<String> tokens = deviceTokenService.getActiveTokens(event.userId());
		if (tokens.isEmpty()) {
			return;
		}

		FcmResult result = firebaseService.sendToTokens(tokens, event.message());
		if (!result.invalidTokens().isEmpty()) {
			deviceTokenService.purgeInvalidTokens(result.invalidTokens());
		}
	}
}
