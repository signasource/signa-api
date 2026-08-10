package com.signasource.signa_api.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.notification.entity.DevicePlatform;
import com.signasource.signa_api.notification.entity.DeviceToken;
import com.signasource.signa_api.notification.repository.DeviceTokenRepository;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    private static final String TOPIC = "global";
    private static final String TOKEN = "device-token";
    private static final String USER_EMAIL = "user@signasource.com";
    private static final String OTHER_USER_EMAIL = "other@signasource.com";
    private static final long ROWS_DELETED = 1L;
    private static final long NO_ROWS_DELETED = 0L;
    private static final List<String> INVALID_TOKENS =
            List.of("invalid-token-1", "invalid-token-2");

    @Mock private DeviceTokenRepository deviceTokenRepository;
    @Mock private FirebaseService firebaseService;

    @InjectMocks private DeviceTokenService deviceTokenService;

    private final User user = User.builder().id(UUID.randomUUID()).email(USER_EMAIL).build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(deviceTokenService, "globalTopic", TOPIC);
    }

    @Test
    void registerTokenCreatesNewTokenAndSubscribes() {
        when(deviceTokenRepository.findByToken(TOKEN)).thenReturn(Optional.empty());

        deviceTokenService.registerToken(user, TOKEN, DevicePlatform.ANDROID);

        ArgumentCaptor<DeviceToken> captor = ArgumentCaptor.forClass(DeviceToken.class);
        verify(deviceTokenRepository).save(captor.capture());
        DeviceToken saved = captor.getValue();
        assertEquals(TOKEN, saved.getToken());
        assertEquals(user, saved.getUser());
        assertEquals(DevicePlatform.ANDROID, saved.getPlatform());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getLastUsedAt());
        verify(firebaseService).subscribeToTopic(List.of(TOKEN), TOPIC);
    }

    @Test
    void registerTokenReassignsExistingTokenToNewUser() {
        User previousOwner = User.builder().id(UUID.randomUUID()).email(OTHER_USER_EMAIL).build();
        DeviceToken existing =
                DeviceToken.builder()
                        .token(TOKEN)
                        .user(previousOwner)
                        .platform(DevicePlatform.ANDROID)
                        .build();
        when(deviceTokenRepository.findByToken(TOKEN)).thenReturn(Optional.of(existing));

        deviceTokenService.registerToken(user, TOKEN, DevicePlatform.IOS);

        assertEquals(user, existing.getUser());
        assertEquals(DevicePlatform.IOS, existing.getPlatform());
        assertNotNull(existing.getLastUsedAt());
        verify(deviceTokenRepository).save(existing);
        verify(firebaseService).subscribeToTopic(List.of(TOKEN), TOPIC);
    }

    @Test
    void registerTokenRefreshesExistingTokenForSameUser() {
        DeviceToken existing =
                DeviceToken.builder()
                        .token(TOKEN)
                        .user(user)
                        .platform(DevicePlatform.ANDROID)
                        .build();
        when(deviceTokenRepository.findByToken(TOKEN)).thenReturn(Optional.of(existing));

        deviceTokenService.registerToken(user, TOKEN, DevicePlatform.WEB);

        assertEquals(user, existing.getUser());
        assertEquals(DevicePlatform.WEB, existing.getPlatform());
        assertNotNull(existing.getLastUsedAt());
        verify(deviceTokenRepository).save(existing);
        verify(firebaseService).subscribeToTopic(List.of(TOKEN), TOPIC);
    }

    @Test
    void registerTokenDefersSubscribeUntilTransactionCommits() {
        when(deviceTokenRepository.findByToken(TOKEN)).thenReturn(Optional.empty());
        TransactionSynchronizationManager.initSynchronization();
        try {
            deviceTokenService.registerToken(user, TOKEN, DevicePlatform.ANDROID);

            verify(deviceTokenRepository).save(any(DeviceToken.class));
            verifyNoInteractions(firebaseService);

            triggerAfterCommit();
            verify(firebaseService).subscribeToTopic(List.of(TOKEN), TOPIC);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void removeTokenDeletesAndUnsubscribes() {
        when(deviceTokenRepository.deleteByTokenAndUser(TOKEN, user)).thenReturn(ROWS_DELETED);

        deviceTokenService.removeToken(user, TOKEN);

        verify(deviceTokenRepository).deleteByTokenAndUser(TOKEN, user);
        verify(firebaseService).unsubscribeFromTopic(List.of(TOKEN), TOPIC);
    }

    @Test
    void removeTokenDoesNotUnsubscribeWhenNothingDeleted() {
        when(deviceTokenRepository.deleteByTokenAndUser(TOKEN, user)).thenReturn(NO_ROWS_DELETED);

        deviceTokenService.removeToken(user, TOKEN);

        verify(deviceTokenRepository).deleteByTokenAndUser(TOKEN, user);
        verifyNoInteractions(firebaseService);
    }

    @Test
    void removeAllTokensForUserDeletesAndUnsubscribesEachToken() {
        when(deviceTokenRepository.findTokensByUserId(user.getId())).thenReturn(List.of(TOKEN));

        deviceTokenService.removeAllTokensForUser(user);

        verify(deviceTokenRepository).deleteByUser(user);
        verify(firebaseService).unsubscribeFromTopic(List.of(TOKEN), TOPIC);
    }

    @Test
    void removeAllTokensForUserSkipsUnsubscribeWhenUserHasNoTokens() {
        when(deviceTokenRepository.findTokensByUserId(user.getId())).thenReturn(List.of());

        deviceTokenService.removeAllTokensForUser(user);

        verify(deviceTokenRepository).deleteByUser(user);
        verify(firebaseService, never()).unsubscribeFromTopic(any(), any());
    }

    @Test
    void getTokensByUserIdReturnsTokens() {
        UUID userId = user.getId();
        when(deviceTokenRepository.findTokensByUserId(userId)).thenReturn(List.of(TOKEN));

        assertEquals(List.of(TOKEN), deviceTokenService.getTokens(userId));
    }

    @Test
    void getTokensByUserIdsReturnsEmptyWhenNoIds() {
        assertEquals(List.of(), deviceTokenService.getTokens(List.of()));
        verify(deviceTokenRepository, never()).findTokensByUserIds(any());
    }

    @Test
    void getTokensByUserIdsReturnsTokens() {
        List<UUID> ids = List.of(user.getId());
        when(deviceTokenRepository.findTokensByUserIds(ids)).thenReturn(List.of(TOKEN));

        assertEquals(List.of(TOKEN), deviceTokenService.getTokens(ids));
    }

    @Test
    void purgeInvalidTokensSkipsWhenEmpty() {
        deviceTokenService.purgeInvalidTokens(List.of());

        verifyNoInteractions(deviceTokenRepository);
    }

    @Test
    void purgeInvalidTokensDeletesTokens() {
        deviceTokenService.purgeInvalidTokens(INVALID_TOKENS);

        verify(deviceTokenRepository).deleteByTokenIn(INVALID_TOKENS);
    }

    private void triggerAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }
}
