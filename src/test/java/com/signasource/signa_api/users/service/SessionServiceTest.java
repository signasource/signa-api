package com.signasource.signa_api.users.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.users.dto.RecordSessionRequest;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserSession;
import com.signasource.signa_api.users.repository.UserSessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock private UserSessionRepository repository;
    @InjectMocks private SessionService service;

    private final User user = User.builder().id(UUID.randomUUID()).build();

    @Test
    void persistsAValidSession() {
        Instant startedAt = Instant.parse("2026-11-06T10:00:00Z");
        service.record(user, new RecordSessionRequest(startedAt, 180, null));

        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(repository).save(saved.capture());
        assertEquals(user, saved.getValue().getUser());
        assertEquals(startedAt, saved.getValue().getStartedAt());
        assertEquals(180, saved.getValue().getDurationSeconds());
    }

    @Test
    void aHeartbeatExtendsTheExistingSessionInsteadOfCreatingAnother() {
        UUID sessionId = UUID.randomUUID();
        UserSession existing =
                UserSession.builder()
                        .user(user)
                        .clientSessionId(sessionId)
                        .startedAt(Instant.now())
                        .durationSeconds(30)
                        .build();
        org.mockito.Mockito.when(repository.findByUserIdAndClientSessionId(user.getId(), sessionId))
                .thenReturn(java.util.Optional.of(existing));

        service.record(user, new RecordSessionRequest(existing.getStartedAt(), 90, sessionId));

        verify(repository).save(existing);
        assertEquals(90, existing.getDurationSeconds());
    }

    @Test
    void rejectsImplausiblyLongSession() {
        RecordSessionRequest request =
                new RecordSessionRequest(
                        Instant.now(), SessionService.MAX_DURATION_SECONDS + 1, null);

        assertThrows(InvalidInputException.class, () -> service.record(user, request));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsAFutureStart() {
        RecordSessionRequest request =
                new RecordSessionRequest(Instant.now().plus(Duration.ofHours(1)), 60, null);

        assertThrows(InvalidInputException.class, () -> service.record(user, request));
        verifyNoInteractions(repository);
    }
}
