package com.signasource.signa_api.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.admin.dto.AdminCourseResponse;
import com.signasource.signa_api.admin.dto.AdminSignResponse;
import com.signasource.signa_api.admin.dto.AdminWaitlistEntryResponse;
import com.signasource.signa_api.admin.service.AdminService;
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
class AdminContentControllerTest {

    @Mock private AdminService adminService;
    @InjectMocks private AdminContentController controller;

    @Test
    void shouldGetCourses() {
        when(adminService.getCourses()).thenReturn(List.of());

        ResponseEntity<List<AdminCourseResponse>> resp = controller.getCourses();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void shouldGetSigns() {
        AdminSignResponse sign =
                new AdminSignResponse(UUID.randomUUID(), "hola", "ONE_HANDED", true, 0, 0);
        when(adminService.getSigns(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sign)));

        ResponseEntity<?> resp = controller.getSigns(null, Pageable.unpaged());

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void shouldGetWaitlist() {
        AdminWaitlistEntryResponse entry =
                new AdminWaitlistEntryResponse(1L, "user@test.com", Instant.now());
        when(adminService.getWaitlist(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        ResponseEntity<?> resp = controller.getWaitlist(Pageable.unpaged());

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }
}
