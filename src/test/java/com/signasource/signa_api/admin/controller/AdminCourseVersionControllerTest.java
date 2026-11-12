package com.signasource.signa_api.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.admin.dto.AdminCourseVersionDetailResponse;
import com.signasource.signa_api.admin.dto.AdminCourseVersionListItemResponse;
import com.signasource.signa_api.admin.dto.CreateCourseRequest;
import com.signasource.signa_api.admin.dto.SaveCourseVersionRequest;
import com.signasource.signa_api.admin.service.AdminCourseVersionService;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
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
class AdminCourseVersionControllerTest {

    @Mock private AdminCourseVersionService service;
    @InjectMocks private AdminCourseVersionController controller;

    private CustomUserDetails adminPrincipal;
    private AdminCourseVersionDetailResponse sampleDetail;

    @BeforeEach
    void setUp() {
        User admin = User.builder().email("admin@signa.com").build();
        adminPrincipal = new CustomUserDetails(admin);
        sampleDetail =
                new AdminCourseVersionDetailResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "1",
                        "DRAFT",
                        null,
                        null,
                        "Test Course",
                        null,
                        true,
                        null,
                        null,
                        "admin@signa.com",
                        null,
                        List.of());
    }

    @Test
    void shouldListVersions() {
        AdminCourseVersionListItemResponse item =
                new AdminCourseVersionListItemResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Test",
                        "1",
                        "DRAFT",
                        null,
                        null,
                        true,
                        null,
                        0,
                        0,
                        0,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null);
        when(service.listVersions()).thenReturn(List.of(item));

        ResponseEntity<List<AdminCourseVersionListItemResponse>> resp = controller.listVersions();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().size());
    }

    @Test
    void shouldGetVersion() {
        UUID id = UUID.randomUUID();
        when(service.getVersion(id)).thenReturn(sampleDetail);

        ResponseEntity<AdminCourseVersionDetailResponse> resp = controller.getVersion(id);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("Test Course", resp.getBody().name());
    }

    @Test
    void shouldCreateCourse() {
        CreateCourseRequest request = new CreateCourseRequest("New Course");
        when(service.createCourse(request, "admin@signa.com")).thenReturn(sampleDetail);

        ResponseEntity<AdminCourseVersionDetailResponse> resp =
                controller.createCourse(request, adminPrincipal);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        verify(service).createCourse(request, "admin@signa.com");
    }

    @Test
    void shouldForkVersion() {
        UUID id = UUID.randomUUID();
        when(service.forkVersion(id, "admin@signa.com")).thenReturn(sampleDetail);

        ResponseEntity<AdminCourseVersionDetailResponse> resp =
                controller.forkVersion(id, adminPrincipal);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        verify(service).forkVersion(id, "admin@signa.com");
    }

    @Test
    void shouldSaveVersion() {
        UUID id = UUID.randomUUID();
        SaveCourseVersionRequest request =
                new SaveCourseVersionRequest("Updated", null, true, null, null, List.of());
        when(service.saveVersion(eq(id), eq(request), eq("admin@signa.com")))
                .thenReturn(sampleDetail);

        ResponseEntity<AdminCourseVersionDetailResponse> resp =
                controller.saveVersion(id, request, adminPrincipal);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(service).saveVersion(id, request, "admin@signa.com");
    }

    @Test
    void shouldPublishVersion() {
        UUID id = UUID.randomUUID();
        when(service.publishVersion(id)).thenReturn(sampleDetail);

        ResponseEntity<AdminCourseVersionDetailResponse> resp = controller.publishVersion(id);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(service).publishVersion(id);
    }
}
