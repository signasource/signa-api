package com.signasource.signa_api.notification.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.notification.dto.FcmResult;
import com.signasource.signa_api.notification.dto.PushMessage;
import com.signasource.signa_api.notification.service.DeviceTokenService;
import com.signasource.signa_api.notification.service.FirebaseService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationPushListenerTest {

    private static final String VALID_TOKEN = "t1";
    private static final String INVALID_TOKEN = "t2";

    @Mock private DeviceTokenService deviceTokenService;
    @Mock private FirebaseService firebaseService;

    @InjectMocks private NotificationPushListener listener;

    private final UUID userId = UUID.randomUUID();
    private final PushMessage message = new PushMessage("title", "body", Map.of());

    @Test
    void skipsWhenUserHasNoTokens() {
        when(deviceTokenService.getTokens(userId)).thenReturn(List.of());

        listener.onNotificationPersisted(new NotificationPersistedEvent(userId, message));

        verifyNoInteractions(firebaseService);
        verify(deviceTokenService, never()).purgeInvalidTokens(any());
    }

    @Test
    void sendsAndPurgesInvalidTokens() {
        when(deviceTokenService.getTokens(userId)).thenReturn(List.of(VALID_TOKEN, INVALID_TOKEN));
        when(firebaseService.sendToTokens(anyList(), any(PushMessage.class)))
                .thenReturn(FcmResult.of(1, 1, List.of(INVALID_TOKEN)));

        listener.onNotificationPersisted(new NotificationPersistedEvent(userId, message));

        verify(firebaseService).sendToTokens(List.of(VALID_TOKEN, INVALID_TOKEN), message);
        verify(deviceTokenService).purgeInvalidTokens(List.of(INVALID_TOKEN));
    }

    @Test
    void doesNotPurgeWhenAllTokensValid() {
        when(deviceTokenService.getTokens(userId)).thenReturn(List.of(VALID_TOKEN));
        when(firebaseService.sendToTokens(anyList(), any(PushMessage.class)))
                .thenReturn(FcmResult.of(1, 0, List.of()));

        listener.onNotificationPersisted(new NotificationPersistedEvent(userId, message));

        verify(deviceTokenService, never()).purgeInvalidTokens(any());
    }
}
