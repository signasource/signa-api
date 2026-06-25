package com.signasource.signa_api.notification.event;

import java.util.UUID;

import com.signasource.signa_api.notification.service.PushMessage;

public record NotificationPersistedEvent(UUID userId, PushMessage message) {
}
