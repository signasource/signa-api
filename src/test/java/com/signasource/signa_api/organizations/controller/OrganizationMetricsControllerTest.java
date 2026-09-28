package com.signasource.signa_api.organizations.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.organizations.dto.ModuleStatsResponse;
import com.signasource.signa_api.organizations.dto.OrganizationOverviewResponse;
import com.signasource.signa_api.organizations.service.OrganizationMetricsService;
import com.signasource.signa_api.users.entity.User;
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
        when(metricsService.getOverview(actor, organizationId)).thenReturn(response);

        ResponseEntity<OrganizationOverviewResponse> result =
                metricsController.getOverview(organizationId, userDetails);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void shouldReturnTheModuleStats() {
        List<ModuleStatsResponse> response = List.of();
        when(metricsService.getModuleStats(actor, organizationId)).thenReturn(response);

        ResponseEntity<List<ModuleStatsResponse>> result =
                metricsController.getModuleStats(organizationId, userDetails);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }
}
