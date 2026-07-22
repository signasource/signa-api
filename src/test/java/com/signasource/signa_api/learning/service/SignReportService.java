package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.dto.CreateSignReportRequest;
import com.signasource.signa_api.learning.entity.ReportReason;
import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignReport;
import com.signasource.signa_api.learning.repository.SignReportRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignReportServiceTest {

    @Mock private SignReportRepository signReportRepository;

    @Mock private SignRepository signRepository;

    @Mock private UserRepository userRepository;

    @InjectMocks private SignReportService signReportService;

    private UUID userId;
    private UUID signId;
    private User user;
    private Sign sign;
    private CreateSignReportRequest request;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        signId = UUID.randomUUID();

        user = User.builder().id(userId).email("test@test.com").build();
        sign = Sign.builder().id(signId).meaning("Prueba").build();

        request =
                new CreateSignReportRequest(
                        signId, ReportReason.UNCLEAR_ANIMATION, "The hand is backwards.");
    }

    @Test
    void shouldCreateReportSuccessfully() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(signRepository.findById(signId)).thenReturn(Optional.of(sign));

        signReportService.createReport(request, userId);

        verify(userRepository).findById(userId);
        verify(signRepository).findById(signId);
        verify(signReportRepository).save(any(SignReport.class));
    }

    @Test
    void shouldThrowExceptionWhenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class, () -> signReportService.createReport(request, userId));

        verify(userRepository).findById(userId);
        verify(signRepository, never()).findById(any());
        verify(signReportRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenSignNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(signRepository.findById(signId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class, () -> signReportService.createReport(request, userId));

        verify(userRepository).findById(userId);
        verify(signRepository).findById(signId);
        verify(signReportRepository, never()).save(any());
    }
}
