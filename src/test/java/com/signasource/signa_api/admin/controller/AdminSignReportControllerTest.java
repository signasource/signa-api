package com.signasource.signa_api.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.admin.dto.AdminSignReportResponse;
import com.signasource.signa_api.admin.dto.UpdateSignReportRequest;
import com.signasource.signa_api.admin.service.AdminService;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.entity.ReportReason;
import com.signasource.signa_api.learning.entity.ReportStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminSignReportControllerTest {

    @Mock private AdminService adminService;
    @InjectMocks private AdminSignReportController controller;

    private AdminSignReportResponse makeReport(UUID id) {
        return new AdminSignReportResponse(
                id,
                UUID.randomUUID(),
                "hola",
                "LSA",
                "ONE_HANDED",
                "user1",
                ReportReason.OTHER,
                "no se entiende",
                ReportStatus.PENDING,
                null,
                null,
                null,
                LocalDateTime.now());
    }

    @Test
    void shouldListSignReports() {
        UUID id = UUID.randomUUID();
        when(adminService.getSignReports(null, null, null, null, null))
                .thenReturn(List.of(makeReport(id)));

        ResponseEntity<List<AdminSignReportResponse>> resp =
                controller.list(null, null, null, null, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().size());
    }

    @Test
    void shouldListSignReportsWithFilters() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 2, 1);
        when(adminService.getSignReports(
                        ReportStatus.PENDING, ReportReason.INCORRECT_SIGN, "hola", from, to))
                .thenReturn(List.of());

        ResponseEntity<List<AdminSignReportResponse>> resp =
                controller.list(
                        ReportStatus.PENDING, ReportReason.INCORRECT_SIGN, "hola", from, to);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void shouldUpdateSignReport() {
        UUID id = UUID.randomUUID();
        UpdateSignReportRequest req = new UpdateSignReportRequest(ReportStatus.RESOLVED, "OK");
        CustomUserDetails userDetails = org.mockito.Mockito.mock(CustomUserDetails.class);
        when(userDetails.getUser()).thenReturn(null);
        when(adminService.updateSignReport(eq(id), any(), any())).thenReturn(makeReport(id));

        ResponseEntity<AdminSignReportResponse> resp = controller.update(id, req, userDetails);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(id, resp.getBody().id());
    }
}
