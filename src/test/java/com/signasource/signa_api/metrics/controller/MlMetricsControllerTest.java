package com.signasource.signa_api.metrics.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.metrics.dto.MlMetricsResponse;
import com.signasource.signa_api.metrics.service.MlMetricsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class MlMetricsControllerTest {

    @Mock private MlMetricsService mlMetricsService;

    @Test
    void shouldDelegateToServiceAndReturn200() {
        MlMetricsController controller = new MlMetricsController(mlMetricsService);
        MlMetricsResponse body = new MlMetricsResponse(42L, List.of());
        when(mlMetricsService.getMlMetrics(any())).thenReturn(body);

        ResponseEntity<MlMetricsResponse> response =
                controller.ml("2026-11-01T00:00:00Z", "2026-11-08T00:00:00Z");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(body, response.getBody());
    }
}
