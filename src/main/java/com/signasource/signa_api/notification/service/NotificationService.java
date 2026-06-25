package com.signasource.signa_api.notification.service;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.notification.dto.NotificationResponse;
import com.signasource.signa_api.notification.dto.PushMessage;
import com.signasource.signa_api.notification.entity.NotificationCode;
import com.signasource.signa_api.notification.entity.NotificationHistory;
import com.signasource.signa_api.notification.entity.NotificationScope;
import com.signasource.signa_api.notification.entity.NotificationTemplate;
import com.signasource.signa_api.notification.event.NotificationPersistedEvent;
import com.signasource.signa_api.notification.repository.NotificationHistoryRepository;
import com.signasource.signa_api.notification.repository.NotificationTemplateRepository;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

	private static final String PUSH_KEY_CODE = "code";
	private static final String PUSH_KEY_NOTIFICATION_ID = "notificationId";

	private final NotificationTemplateRepository templateRepository;
	private final NotificationHistoryRepository historyRepository;
	private final UserRepository userRepository;
	private final FirebaseService firebaseService;
	private final ApplicationEventPublisher eventPublisher;

	@Value("${firebase.global-topic:global}")
	private String globalTopic;

	@Transactional
	public void notifyUser(UUID userId, NotificationCode code, Map<String, String> data) {
		NotificationTemplate template = getEnabledIndividualTemplate(code);
		persistAndQueuePush(getUser(userId), template, data);
	}

	@Transactional
	public void notifyUsers(Collection<UUID> userIds, NotificationCode code, Map<String, String> data) {
		if (userIds == null || userIds.isEmpty()) {
			return;
		}
		NotificationTemplate template = getEnabledIndividualTemplate(code);
		for (UUID userId : userIds) {
			persistAndQueuePush(getUser(userId), template, data);
		}
	}

	public void broadcastGlobal(NotificationCode code, String titleOverride, String bodyOverride) {
		NotificationTemplate template = getRandomEnabledTemplate(code);
		if (template.getScope() != NotificationScope.GLOBAL) {
			throw new InvalidInputException("Notification " + code + " is not a global notification");
		}
		String title = StringUtils.hasText(titleOverride) ? titleOverride : template.getDefaultTitle();
		String body = StringUtils.hasText(bodyOverride) ? bodyOverride : template.getDefaultBody();
		PushMessage message = new PushMessage(title, body, Map.of(PUSH_KEY_CODE, code.name()));
		firebaseService.sendToTopic(globalTopic, message);
	}

	@Transactional(readOnly = true)
	public Page<NotificationResponse> getInbox(User user, Pageable pageable) {
		return historyRepository.findByUserOrderBySentAtDesc(user, pageable).map(NotificationResponse::from);
	}

	@Transactional(readOnly = true)
	public List<NotificationResponse> getUnread(User user) {
		return historyRepository.findByUserAndReadFalseOrderBySentAtDesc(user).stream().map(NotificationResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public long countUnread(User user) {
		return historyRepository.countByUserAndReadFalse(user);
	}

	@Transactional
	public void markAsRead(User user, Long notificationId) {
		NotificationHistory history = historyRepository.findByIdAndUser(notificationId, user)
				.orElseThrow(() -> new NotFoundException("Notification not found"));
		if (!history.isRead()) {
			history.setRead(true);
			history.setReadAt(Instant.now());
			historyRepository.save(history);
		}
	}

	@Transactional
	public void markAllAsRead(User user) {
		historyRepository.markAllAsRead(user, Instant.now());
	}

	private void persistAndQueuePush(User user, NotificationTemplate template, Map<String, String> data) {
		String title = NotificationTextRenderer.render(template.getDefaultTitle(), data);
		String body = NotificationTextRenderer.render(template.getDefaultBody(), data);

		NotificationHistory history = historyRepository.save(NotificationHistory.builder().user(user).template(template)
				.title(title).body(body).sentAt(Instant.now()).read(false).metadata(data).build());

		PushMessage message = new PushMessage(title, body, buildPushData(template, history, data));
		eventPublisher.publishEvent(new NotificationPersistedEvent(user.getId(), message));
	}

	private Map<String, String> buildPushData(NotificationTemplate template, NotificationHistory history,
			Map<String, String> data) {
		Map<String, String> push = new HashMap<>();
		if (data != null) {
			push.putAll(data);
		}
		push.put(PUSH_KEY_CODE, template.getCode().name());
		push.put(PUSH_KEY_NOTIFICATION_ID, String.valueOf(history.getId()));
		return push;
	}

	private NotificationTemplate getEnabledIndividualTemplate(NotificationCode code) {
		NotificationTemplate template = getRandomEnabledTemplate(code);
		if (template.getScope() != NotificationScope.INDIVIDUAL) {
			throw new InvalidInputException("Notification " + code + " is not an individual notification");
		}
		return template;
	}

	private NotificationTemplate getRandomEnabledTemplate(NotificationCode code) {
		List<NotificationTemplate> templates = templateRepository.findByCodeAndEnabledTrue(code);
		if (templates.isEmpty()) {
			throw new NotFoundException("No enabled notification template found for: " + code);
		}
		if (templates.size() == 1) {
			return templates.get(0);
		}
		return templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
	}

	private User getUser(UUID userId) {
		return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
	}
}
