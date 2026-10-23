package com.signasource.signa_api.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.admin.dto.AdminSignReportGroupResponse;
import com.signasource.signa_api.admin.dto.UpdateSignReportGroupRequest;
import com.signasource.signa_api.admin.service.AdminService;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.entity.ReportReason;
import com.signasource.signa_api.learning.entity.ReportStatus;
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

    private AdminSignReportGroupResponse makeGroup(UUID signId) {
        return new AdminSignReportGroupResponse(
                signId,
                "hola",
                "LSA",
                "ONE_HANDED",
                2,
                ReportReason.OTHER,
                ReportStatus.PENDING,
                null,
                List.of());
    }

    @Test
    void shouldListSignReportGroups() {
        UUID signId = UUID.randomUUID();
        when(adminService.getSignReportGroups(null)).thenReturn(List.of(makeGroup(signId)));

        ResponseEntity<List<AdminSignReportGroupResponse>> resp = controller.list(null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().size());
    }

    @Test
    void shouldListSignReportGroupsWithStatusFilter() {
        when(adminService.getSignReportGroups(ReportStatus.PENDING)).thenReturn(List.of());

        ResponseEntity<List<AdminSignReportGroupResponse>> resp =
                controller.list(ReportStatus.PENDING);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void shouldUpdateSignReportGroup() {
        UUID signId = UUID.randomUUID();
        UpdateSignReportGroupRequest req =
                new UpdateSignReportGroupRequest(ReportStatus.RESOLVED, "OK");
        CustomUserDetails userDetails = org.mockito.Mockito.mock(CustomUserDetails.class);
        when(userDetails.getUser()).thenReturn(null);
        when(adminService.updateSignReportGroup(eq(signId), any(), any()))
                .thenReturn(makeGroup(signId));

        ResponseEntity<AdminSignReportGroupResponse> resp =
                controller.updateGroup(signId, req, userDetails);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(signId, resp.getBody().signId());
    }
}
