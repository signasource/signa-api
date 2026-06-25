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

/**
 * Manages the lifecycle of FCM device tokens. Every registered token is kept
 * subscribed to the global topic so it can receive broadcasts. Firebase calls
 * are deferred until the surrounding transaction commits, so they never widen
 * the database transaction nor act on a change that ends up rolled back.
 */
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
		DeviceToken deviceToken = deviceTokenRepository.findByToken(token).map(existing -> {
			existing.setUser(user);
			existing.setPlatform(platform);
			existing.setLastUsedAt(now);
			return existing;
		}).orElseGet(() -> DeviceToken.builder().token(token).user(user).platform(platform).createdAt(now)
				.lastUsedAt(now).build());

		deviceTokenRepository.save(deviceToken);
		subscribeToGlobalTopic(List.of(token));
		log.info("Registered device token for user {} on platform {}", user.getId(), platform);
	}

	@Transactional
	public void removeToken(User user, String token) {
		deviceTokenRepository.deleteByTokenAndUser(token, user);
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
		// FCM already rejected these tokens as unregistered, so it has dropped
		// them from every topic; we only need to delete our own copy.
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
