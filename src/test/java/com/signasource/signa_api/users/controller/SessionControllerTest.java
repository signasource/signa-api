package com.signasource.signa_api.users.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.users.dto.RecordSessionRequest;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.service.SessionService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    @Mock private SessionService sessionService;
    @InjectMocks private SessionController controller;

    @Test
    void recordsTheSessionForTheAuthenticatedUser() {
        User user = User.builder().id(UUID.randomUUID()).build();
        CustomUserDetails principal = new CustomUserDetails(user);
        RecordSessionRequest request = new RecordSessionRequest(Instant.now(), 120);

        ResponseEntity<Void> response = controller.record(principal, request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(sessionService).record(user, request);
    }
}
