package com.signasource.signa_api.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.admin.dto.AdminOrganizationResponse;
import com.signasource.signa_api.admin.dto.UpdateOrganizationStatusRequest;
import com.signasource.signa_api.admin.service.AdminService;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminOrganizationControllerTest {

    @Mock private AdminService adminService;
    @InjectMocks private AdminOrganizationController controller;

    private AdminOrganizationResponse makeOrg(UUID id, OrganizationStatus status) {
        return new AdminOrganizationResponse(
                id,
                "Test Org",
                status,
                "30-12345678-9",
                "test@test.com",
                null,
                50,
                Instant.now(),
                0,
                "Admin User",
                "admin@test.com");
    }

    @Test
    void shouldListOrganizations() {
        UUID id = UUID.randomUUID();
        when(adminService.getOrganizations(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(makeOrg(id, OrganizationStatus.PENDING))));

        ResponseEntity<?> resp = controller.list(null, Pageable.unpaged());

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void shouldGetOrganization() {
        UUID id = UUID.randomUUID();
        when(adminService.getOrganization(id)).thenReturn(makeOrg(id, OrganizationStatus.ACTIVE));

        ResponseEntity<AdminOrganizationResponse> resp = controller.get(id);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(id, resp.getBody().id());
    }

    @Test
    void shouldUpdateOrganizationStatus() {
        UUID id = UUID.randomUUID();
        UpdateOrganizationStatusRequest req =
                new UpdateOrganizationStatusRequest(OrganizationStatus.ACTIVE, List.of());
        when(adminService.updateOrganizationStatus(eq(id), any()))
                .thenReturn(makeOrg(id, OrganizationStatus.ACTIVE));

        ResponseEntity<AdminOrganizationResponse> resp = controller.updateStatus(id, req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(OrganizationStatus.ACTIVE, resp.getBody().status());
    }
}
