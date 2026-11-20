package com.signasource.signa_api.organizations.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.organizations.dto.ModuleStatsResponse;
import com.signasource.signa_api.organizations.dto.OrganizationActivityResponse;
import com.signasource.signa_api.organizations.dto.OrganizationOverviewResponse;
import com.signasource.signa_api.organizations.service.MetricsPeriod;
import com.signasource.signa_api.organizations.service.OrganizationMetricsService;
import com.signasource.signa_api.users.entity.User;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class OrganizationMetricsControllerTest {

    @Mock private OrganizationMetricsService metricsService;

    @InjectMocks private OrganizationMetricsController metricsController;

    private User actor;
    private CustomUserDetails userDetails;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        actor = mock(User.class);
        userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUser()).thenReturn(actor);
        organizationId = UUID.randomUUID();
    }

    @Test
    void shouldReturnTheOverview() {
        OrganizationOverviewResponse response = new OrganizationOverviewResponse(null, null, null);
        UUID courseId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        when(metricsService.getOverview(
                        actor, organizationId, courseId, MetricsPeriod.of(from, to)))
                .thenReturn(response);

        ResponseEntity<OrganizationOverviewResponse> result =
                metricsController.getOverview(organizationId, courseId, from, to, userDetails);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void shouldReturnTheModuleStats() {
        List<ModuleStatsResponse> response = List.of();
        when(metricsService.getModuleStats(actor, organizationId, null, MetricsPeriod.ALL_TIME))
                .thenReturn(response);

        ResponseEntity<List<ModuleStatsResponse>> result =
                metricsController.getModuleStats(organizationId, null, null, null, userDetails);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void shouldReturnTheActivityOfAPeriod() {
        LocalDate from = LocalDate.of(2026, 10, 6);
        LocalDate to = LocalDate.of(2026, 10, 12);
        OrganizationActivityResponse response =
                new OrganizationActivityResponse(from, to, null, List.of());
        when(metricsService.getActivity(actor, organizationId, null, MetricsPeriod.of(from, to)))
                .thenReturn(response);

        ResponseEntity<OrganizationActivityResponse> result =
                metricsController.getActivity(organizationId, from, to, null, userDetails);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void shouldRejectAPeriodWithOnlyOneDate() {
        LocalDate from = LocalDate.of(2026, 9, 1);

        assertThrows(
                InvalidInputException.class,
                () -> metricsController.getOverview(organizationId, null, from, null, userDetails));
        verifyNoInteractions(metricsService);
    }
}
