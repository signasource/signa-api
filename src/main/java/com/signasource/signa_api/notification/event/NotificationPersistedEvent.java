package com.signasource.signa_api.notification.event;

import com.signasource.signa_api.notification.dto.PushMessage;
import java.util.UUID;

public record NotificationPersistedEvent(UUID userId, PushMessage message) {}
