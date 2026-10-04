package com.signasource.signa_api.waitlist.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.signasource.signa_api.waitlist.dto.WaitlistRequest;
import com.signasource.signa_api.waitlist.service.WaitlistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class WaitlistControllerTest {

    @Mock private WaitlistService waitlistService;

    @InjectMocks private WaitlistController controller;

    @Test
    void subscribe_returnsAccepted() {
        ResponseEntity<Void> response = controller.subscribe(new WaitlistRequest("a@b.com"));

        verify(waitlistService).subscribe("a@b.com");
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    }
}
