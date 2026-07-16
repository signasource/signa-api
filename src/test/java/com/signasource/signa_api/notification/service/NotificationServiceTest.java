package com.signasource.signa_api.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final String TOPIC = "global";
    private static final String USER_EMAIL = "a@b.com";
    private static final String OTHER_USER_EMAIL = "c@d.com";
    private static final String TEMPLATE_TITLE = "Hola";
    private static final String TEMPLATE_BODY = "Hola {{name}}";
    private static final String DATA_KEY = "name";
    private static final String DATA_VALUE = "Ana";
    private static final String RENDERED_BODY = "Hola Ana";
    private static final String DEFAULT_TITLE = "Default title";
    private static final String DEFAULT_BODY = "Default body";
    private static final String OVERRIDE_TITLE = "Custom";
    private static final String OVERRIDE_BODY = "Custom body";

    @Mock private NotificationTemplateRepository templateRepository;
    @Mock private NotificationHistoryRepository historyRepository;
    @Mock private UserRepository userRepository;
    @Mock private FirebaseService firebaseService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private NotificationService notificationService;

    private User user;
    private NotificationTemplate individualTemplate;

    @BeforeEach
    void setUp() {
        user = User.builder().id(UUID.randomUUID()).email(USER_EMAIL).build();
        individualTemplate =
                template(
                        NotificationCode.DAILY_REMINDER,
                        NotificationScope.INDIVIDUAL,
                        true,
                        TEMPLATE_TITLE,
                        TEMPLATE_BODY);
        ReflectionTestUtils.setField(notificationService, "globalTopic", TOPIC);
    }

    @Test
    void notifyUserPersistsRenderedHistoryAndPublishesEvent() {
        when(templateRepository.findByCodeAndEnabledTrue(NotificationCode.DAILY_REMINDER))
                .thenReturn(List.of(individualTemplate));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(historyRepository.save(any(NotificationHistory.class))).thenAnswer(returnsFirstArg());

        notificationService.notifyUser(
                user.getId(), NotificationCode.DAILY_REMINDER, Map.of(DATA_KEY, DATA_VALUE));

        ArgumentCaptor<NotificationHistory> historyCaptor =
                ArgumentCaptor.forClass(NotificationHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertEquals(RENDERED_BODY, historyCaptor.getValue().getBody());
        assertFalse(historyCaptor.getValue().isRead());

        ArgumentCaptor<NotificationPersistedEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationPersistedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(user.getId(), eventCaptor.getValue().userId());
        assertEquals(RENDERED_BODY, eventCaptor.getValue().message().body());
    }

    @Test
    void notifyUserThrowsWhenNoEnabledTemplates() {
        when(templateRepository.findByCodeAndEnabledTrue(NotificationCode.DAILY_REMINDER))
                .thenReturn(List.of());

        assertThrows(
                NotFoundException.class,
                () ->
                        notificationService.notifyUser(
                                user.getId(), NotificationCode.DAILY_REMINDER, Map.of()));
        verify(historyRepository, never()).save(any());
    }

    @Test
    void notifyUserThrowsWhenTemplateIsGlobal() {
        NotificationTemplate global =
                template(
                        NotificationCode.GLOBAL_ANNOUNCEMENT,
                        NotificationScope.GLOBAL,
                        true,
                        "t",
                        "b");
        when(templateRepository.findByCodeAndEnabledTrue(NotificationCode.GLOBAL_ANNOUNCEMENT))
                .thenReturn(List.of(global));

        assertThrows(
                InvalidInputException.class,
                () ->
                        notificationService.notifyUser(
                                user.getId(), NotificationCode.GLOBAL_ANNOUNCEMENT, Map.of()));
    }

    @Test
    void notifyUserThrowsWhenUserMissing() {
        when(templateRepository.findByCodeAndEnabledTrue(NotificationCode.DAILY_REMINDER))
                .thenReturn(List.of(individualTemplate));
        when(userRepository.findById(user.getId())).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () ->
                        notificationService.notifyUser(
                                user.getId(), NotificationCode.DAILY_REMINDER, Map.of()));
    }

    @Test
    void notifyUserPicksRandomTemplateFromMultiple() {
        NotificationTemplate other =
                template(
                        NotificationCode.DAILY_REMINDER,
                        NotificationScope.INDIVIDUAL,
                        true,
                        "Otro",
                        "Otro body");
        when(templateRepository.findByCodeAndEnabledTrue(NotificationCode.DAILY_REMINDER))
                .thenReturn(List.of(individualTemplate, other));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(historyRepository.save(any(NotificationHistory.class))).thenAnswer(returnsFirstArg());

        notificationService.notifyUser(user.getId(), NotificationCode.DAILY_REMINDER, Map.of());

        verify(historyRepository).save(any(NotificationHistory.class));
        verify(eventPublisher).publishEvent(any(NotificationPersistedEvent.class));
    }

    @Test
    void notifyUsersDoesNothingWhenEmpty() {
        notificationService.notifyUsers(List.of(), NotificationCode.DAILY_REMINDER, Map.of());

        verify(templateRepository, never()).findByCodeAndEnabledTrue(any());
        verify(eventPublisher, never()).publishEvent(any(NotificationPersistedEvent.class));
    }

    @Test
    void notifyUsersNotifiesEachUser() {
        User other = User.builder().id(UUID.randomUUID()).email(OTHER_USER_EMAIL).build();
        when(templateRepository.findByCodeAndEnabledTrue(NotificationCode.DAILY_REMINDER))
                .thenReturn(List.of(individualTemplate));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.findById(other.getId())).thenReturn(Optional.of(other));
        when(historyRepository.save(any(NotificationHistory.class))).thenAnswer(returnsFirstArg());

        notificationService.notifyUsers(
                List.of(user.getId(), other.getId()), NotificationCode.DAILY_REMINDER, Map.of());

        verify(historyRepository, org.mockito.Mockito.times(2)).save(any());
        verify(eventPublisher, org.mockito.Mockito.times(2))
                .publishEvent(any(NotificationPersistedEvent.class));
    }

    @Test
    void broadcastGlobalSendsToTopicWithDefaults() {
        NotificationTemplate global =
                template(
                        NotificationCode.GLOBAL_ANNOUNCEMENT,
                        NotificationScope.GLOBAL,
                        true,
                        DEFAULT_TITLE,
                        DEFAULT_BODY);
        when(templateRepository.findByCodeAndEnabledTrue(NotificationCode.GLOBAL_ANNOUNCEMENT))
                .thenReturn(List.of(global));

        notificationService.broadcastGlobal(NotificationCode.GLOBAL_ANNOUNCEMENT, null, null);

        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(firebaseService).sendToTopic(eq(TOPIC), captor.capture());
        assertEquals(DEFAULT_TITLE, captor.getValue().title());
        assertEquals(DEFAULT_BODY, captor.getValue().body());
    }

    @Test
    void broadcastGlobalUsesOverrides() {
        NotificationTemplate global =
                template(
                        NotificationCode.GLOBAL_ANNOUNCEMENT,
                        NotificationScope.GLOBAL,
                        true,
                        DEFAULT_TITLE,
                        DEFAULT_BODY);
        when(templateRepository.findByCodeAndEnabledTrue(NotificationCode.GLOBAL_ANNOUNCEMENT))
                .thenReturn(List.of(global));

        notificationService.broadcastGlobal(
                NotificationCode.GLOBAL_ANNOUNCEMENT, OVERRIDE_TITLE, OVERRIDE_BODY);

        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(firebaseService).sendToTopic(eq(TOPIC), captor.capture());
        assertEquals(OVERRIDE_TITLE, captor.getValue().title());
        assertEquals(OVERRIDE_BODY, captor.getValue().body());
    }

    @Test
    void broadcastGlobalRejectsIndividualTemplate() {
        when(templateRepository.findByCodeAndEnabledTrue(NotificationCode.DAILY_REMINDER))
                .thenReturn(List.of(individualTemplate));

        assertThrows(
                InvalidInputException.class,
                () ->
                        notificationService.broadcastGlobal(
                                NotificationCode.DAILY_REMINDER, null, null));
        verify(firebaseService, never()).sendToTopic(any(), any());
    }

    @Test
    void getInboxMapsHistoryToResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        when(historyRepository.findByUserOrderBySentAtDesc(user, pageable))
                .thenReturn(new PageImpl<>(List.of(history(false))));

        var page = notificationService.getInbox(user, pageable);

        assertEquals(1, page.getTotalElements());
        assertEquals(NotificationCode.DAILY_REMINDER, page.getContent().get(0).code());
    }

    @Test
    void getUnreadMapsHistoryToResponse() {
        when(historyRepository.findByUserAndReadFalseOrderBySentAtDesc(user))
                .thenReturn(List.of(history(false)));

        List<NotificationResponse> unread = notificationService.getUnread(user);

        assertEquals(1, unread.size());
        assertFalse(unread.get(0).read());
    }

    @Test
    void countUnreadReturnsRepositoryCount() {
        when(historyRepository.countByUserAndReadFalse(user)).thenReturn(3L);

        assertEquals(3L, notificationService.countUnread(user));
    }

    @Test
    void markAsReadUpdatesUnreadNotification() {
        NotificationHistory history = history(false);
        when(historyRepository.findByIdAndUser(5L, user)).thenReturn(Optional.of(history));

        notificationService.markAsRead(user, 5L);

        assertTrue(history.isRead());
        assertNotNull(history.getReadAt());
        verify(historyRepository).save(history);
    }

    @Test
    void markAsReadIsNoOpWhenAlreadyRead() {
        NotificationHistory history = history(true);
        when(historyRepository.findByIdAndUser(5L, user)).thenReturn(Optional.of(history));

        notificationService.markAsRead(user, 5L);

        verify(historyRepository, never()).save(any());
    }

    @Test
    void markAsReadThrowsWhenNotFound() {
        when(historyRepository.findByIdAndUser(5L, user)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> notificationService.markAsRead(user, 5L));
    }

    @Test
    void markAllAsReadDelegatesToRepository() {
        notificationService.markAllAsRead(user);

        verify(historyRepository).markAllAsRead(eq(user), any(Instant.class));
    }

    private NotificationTemplate template(
            NotificationCode code,
            NotificationScope scope,
            boolean enabled,
            String title,
            String body) {
        return NotificationTemplate.builder()
                .id(1L)
                .code(code)
                .scope(scope)
                .enabled(enabled)
                .schedulable(false)
                .defaultTitle(title)
                .defaultBody(body)
                .build();
    }

    private NotificationHistory history(boolean read) {
        return NotificationHistory.builder()
                .id(5L)
                .user(user)
                .template(individualTemplate)
                .title("t")
                .body("b")
                .sentAt(Instant.now())
                .read(read)
                .build();
    }
}
