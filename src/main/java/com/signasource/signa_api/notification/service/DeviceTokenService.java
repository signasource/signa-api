package com.signasource.signa_api.notification.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.signasource.signa_api.notification.entity.DeviceToken;
import com.signasource.signa_api.notification.entity.DevicePlatform;
import com.signasource.signa_api.notification.repository.DeviceTokenRepository;
import com.signasource.signa_api.users.entity.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceTokenService {

	private final DeviceTokenRepository deviceTokenRepository;
	private final FirebaseService firebaseService;

	@Value("${firebase.global-topic:global}")
	private String globalTopic;

	@Transactional
	public void registerToken(User user, String token, DevicePlatform platform) {
		Instant now = Instant.now();
		DeviceToken deviceToken = deviceTokenRepository.findByToken(token).orElse(null);

		if (deviceToken == null) {
			deviceToken = DeviceToken.builder().token(token).user(user).platform(platform).createdAt(now)
					.lastUsedAt(now).build();
			log.info("Registered new device token for user {} on platform {}", user.getId(), platform);
		} else {
			UUID previousOwnerId = deviceToken.getUser().getId();
			if (previousOwnerId.equals(user.getId())) {
				log.info("Refreshed device token for user {} on platform {}", user.getId(), platform);
			} else {
				log.info("Reassigned device token from user {} to user {} on platform {}", previousOwnerId,
						user.getId(), platform);
			}
			deviceToken.setUser(user);
			deviceToken.setPlatform(platform);
			deviceToken.setLastUsedAt(now);
		}

		deviceTokenRepository.save(deviceToken);
		subscribeToGlobalTopic(List.of(token));
	}

	@Transactional
	public void removeToken(User user, String token) {
		long deleted = deviceTokenRepository.deleteByTokenAndUser(token, user);
		if (deleted == 0) {
			// Token is absent or owned by another user: leave its topic
			// subscription untouched, otherwise we would silently unsubscribe a
			// token that belongs to someone else.
			log.info("No device token removed for user {} (absent or not owned)", user.getId());
			return;
		}
		unsubscribeFromGlobalTopic(List.of(token));
		log.info("Removed device token for user {}", user.getId());
	}

	@Transactional
	public void removeAllTokensForUser(User user) {
		List<String> tokens = deviceTokenRepository.findTokensByUserId(user.getId());
		deviceTokenRepository.deleteByUser(user);
		unsubscribeFromGlobalTopic(tokens);
		log.info("Removed all {} device token(s) for user {}", tokens.size(), user.getId());
	}

	@Transactional(readOnly = true)
	public List<String> getTokens(UUID userId) {
		return deviceTokenRepository.findTokensByUserId(userId);
	}

	@Transactional(readOnly = true)
	public List<String> getTokens(Collection<UUID> userIds) {
		if (userIds == null || userIds.isEmpty()) {
			return List.of();
		}
		return deviceTokenRepository.findTokensByUserIds(userIds);
	}

	@Transactional
	public void purgeInvalidTokens(Collection<String> invalidTokens) {
		if (invalidTokens == null || invalidTokens.isEmpty()) {
			return;
		}
		deviceTokenRepository.deleteByTokenIn(invalidTokens);
		log.info("Purged {} invalid device token(s)", invalidTokens.size());
	}

	private void subscribeToGlobalTopic(List<String> tokens) {
		afterCommit(() -> firebaseService.subscribeToTopic(tokens, globalTopic));
	}

	private void unsubscribeFromGlobalTopic(List<String> tokens) {
		if (tokens.isEmpty()) {
			return;
		}
		afterCommit(() -> firebaseService.unsubscribeFromTopic(tokens, globalTopic));
	}

	/**
	 * Runs the action once the current transaction commits, or immediately when no
	 * transaction is active. Keeps the (network-bound) FCM call out of the
	 * transaction and avoids acting on changes that get rolled back.
	 */
	private void afterCommit(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					action.run();
				}
			});
		} else {
			action.run();
		}
	}
}
