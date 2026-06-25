package com.signasource.signa_api.notification.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.signasource.signa_api.notification.entity.DeviceToken;
import com.signasource.signa_api.notification.entity.DevicePlatform;
import com.signasource.signa_api.notification.repository.DeviceTokenRepository;
import com.signasource.signa_api.users.entity.User;

import lombok.RequiredArgsConstructor;

@Service
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
		firebaseService.subscribeToTopic(List.of(token), globalTopic);
	}

	@Transactional
	public void removeToken(User user, String token) {
		deviceTokenRepository.deleteByTokenAndUser(token, user);
		firebaseService.unsubscribeFromTopic(List.of(token), globalTopic);
	}

	@Transactional
	public void removeAllTokensForUser(User user) {
		deviceTokenRepository.deleteByUser(user);
	}

	@Transactional(readOnly = true)
	public List<String> getActiveTokens(UUID userId) {
		return deviceTokenRepository.findTokensByUserId(userId);
	}

	@Transactional(readOnly = true)
	public List<String> getActiveTokens(Collection<UUID> userIds) {
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
	}
}
