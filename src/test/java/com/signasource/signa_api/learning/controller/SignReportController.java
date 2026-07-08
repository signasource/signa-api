package com.signasource.signa_api.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.dto.CreateSignReportRequest;
import com.signasource.signa_api.learning.service.SignReportService;
import com.signasource.signa_api.users.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class SignReportControllerTest {

    @Mock private SignReportService signReportService;

    @InjectMocks private SignReportController signReportController;

    @Test
    void testSubmitReport() {
        UUID userId = UUID.randomUUID();
        User mockUser = User.builder().id(userId).build();
        CustomUserDetails userDetails = new CustomUserDetails(mockUser);

        CreateSignReportRequest request =
                new CreateSignReportRequest(UUID.randomUUID(), "Razón", "Desc");

        doNothing()
                .when(signReportService)
                .createReport(any(CreateSignReportRequest.class), eq(userId));

        ResponseEntity<Void> response = signReportController.submitReport(request, userDetails);

        verify(signReportService).createReport(request, userId);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }
}
