package com.signasource.signa_api.notification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.notification.dto.NotificationResponse;
import com.signasource.signa_api.notification.dto.UnreadCountResponse;
import com.signasource.signa_api.notification.entity.NotificationCode;
import com.signasource.signa_api.notification.service.NotificationService;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock private NotificationService notificationService;
    @Mock private CustomUserDetails userDetails;

    @InjectMocks private NotificationController notificationController;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("mateo");
    }

    private NotificationResponse notification() {
        return new NotificationResponse(
                1L,
                NotificationCode.FRIEND_REQUEST_RECEIVED,
                "Nueva solicitud de amistad",
                "Tomás te envió una solicitud de amistad.",
                false,
                Instant.now(),
                null,
                Map.of("friend", "Tomás"));
    }

    @Test
    void getInbox_ReturnsOkWithThePage() {
        when(userDetails.getUser()).thenReturn(user);
        Page<NotificationResponse> page = new PageImpl<>(List.of(notification()));
        when(notificationService.getInbox(eq(user), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<NotificationResponse>> response =
                notificationController.getInbox(userDetails, 0, 30);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getContent().size());
    }

    @Test
    void getInbox_CapsThePageSize() {
        when(userDetails.getUser()).thenReturn(user);
        when(notificationService.getInbox(eq(user), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        notificationController.getInbox(userDetails, 0, 5000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).getInbox(eq(user), captor.capture());
        assertEquals(100, captor.getValue().getPageSize());
    }

    /** A negative page or size must not reach Spring Data, which would throw. */
    @Test
    void getInbox_NormalisesNegativeInput() {
        when(userDetails.getUser()).thenReturn(user);
        when(notificationService.getInbox(eq(user), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        notificationController.getInbox(userDetails, -3, -10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).getInbox(eq(user), captor.capture());
        assertEquals(PageRequest.of(0, 1), captor.getValue());
    }

    @Test
    void getUnread_ReturnsOkWithTheList() {
        when(userDetails.getUser()).thenReturn(user);
        when(notificationService.getUnread(user)).thenReturn(List.of(notification()));

        ResponseEntity<List<NotificationResponse>> response =
                notificationController.getUnread(userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void getUnreadCount_ReturnsTheCount() {
        when(userDetails.getUser()).thenReturn(user);
        when(notificationService.countUnread(user)).thenReturn(4L);

        ResponseEntity<UnreadCountResponse> response =
                notificationController.getUnreadCount(userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(4L, response.getBody().unreadCount());
    }

    @Test
    void markAsRead_ReturnsNoContent() {
        when(userDetails.getUser()).thenReturn(user);
        doNothing().when(notificationService).markAsRead(user, 7L);

        ResponseEntity<Void> response = notificationController.markAsRead(userDetails, 7L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(notificationService).markAsRead(user, 7L);
    }

    @Test
    void markAllAsRead_ReturnsNoContent() {
        when(userDetails.getUser()).thenReturn(user);
        doNothing().when(notificationService).markAllAsRead(user);

        ResponseEntity<Void> response = notificationController.markAllAsRead(userDetails);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(notificationService).markAllAsRead(user);
    }
}
