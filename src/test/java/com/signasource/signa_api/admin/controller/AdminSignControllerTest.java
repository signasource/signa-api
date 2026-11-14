package com.signasource.signa_api.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.admin.dto.AdminSignDetailResponse;
import com.signasource.signa_api.admin.dto.CreateAdminSignRequest;
import com.signasource.signa_api.admin.dto.UpdateSignRequest;
import com.signasource.signa_api.admin.service.AdminSignService;
import com.signasource.signa_api.learning.entity.Handedness;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class AdminSignControllerTest {

    @Mock private AdminSignService service;
    @InjectMocks private AdminSignController controller;

    private static AdminSignDetailResponse detail(UUID id, String meaning) {
        return new AdminSignDetailResponse(
                id,
                meaning,
                "ONE_HANDED",
                "LSA",
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                List.of());
    }

    @Test
    void createReturns201() {
        var request = new CreateAdminSignRequest("hola", Handedness.ONE_HANDED, null);
        when(service.create(request)).thenReturn(detail(UUID.randomUUID(), "hola"));

        var response = controller.create(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void updateAndUploadDelegate() {
        UUID id = UUID.randomUUID();
        var update = new UpdateSignRequest("saludo", null, null);
        when(service.update(id, update)).thenReturn(detail(id, "saludo"));
        var file = new MockMultipartFile("file", "a.glb", null, new byte[] {1});
        when(service.uploadAnimation(id, file, AdminSignService.View.LATERAL))
                .thenReturn(detail(id, "saludo"));

        assertEquals("saludo", controller.update(id, update).getBody().meaning());
        assertEquals(
                HttpStatus.OK, controller.uploadAnimation(id, file, "lateral").getStatusCode());
    }

    @Test
    void deleteReturns204() {
        UUID id = UUID.randomUUID();

        assertEquals(HttpStatus.NO_CONTENT, controller.delete(id).getStatusCode());
        verify(service).delete(id);
    }
}
