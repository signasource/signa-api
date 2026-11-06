package com.signasource.signa_api.metrics.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse;
import com.signasource.signa_api.metrics.service.GrowthMetricsService;
import com.signasource.signa_api.metrics.service.MetricsRange;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class GrowthMetricsControllerTest {

    @Mock private GrowthMetricsService service;
    @InjectMocks private GrowthMetricsController controller;

    private static final GrowthMetricsResponse SAMPLE =
            new GrowthMetricsResponse(
                    new GrowthMetricsResponse.UsersSection(1, 0, List.of()),
                    new GrowthMetricsResponse.EngagementSection(0, 0, 0, 0, List.of()),
                    new GrowthMetricsResponse.WaitlistSection(0, 0, List.of()),
                    new GrowthMetricsResponse.RevenueSection(
                            BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, List.of()),
                    new GrowthMetricsResponse.GemsSection(0, 0, List.of()),
                    new GrowthMetricsResponse.SeatsSection(0, 0, 0),
                    List.of(),
                    0);

    @Test
    void returnsTheSnapshotForTheRequestedWindow() {
        ArgumentCaptor<MetricsRange> range = ArgumentCaptor.forClass(MetricsRange.class);
        when(service.getGrowthMetrics(range.capture())).thenReturn(SAMPLE);

        ResponseEntity<GrowthMetricsResponse> response =
                controller.growth("2026-11-01T00:00:00Z", "2026-11-08T00:00:00Z");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(SAMPLE, response.getBody());
        verify(service).getGrowthMetrics(range.getValue());
    }

    @Test
    void treatsMissingParamsAsTheWholeHistory() {
        when(service.getGrowthMetrics(MetricsRange.ALL_TIME)).thenReturn(SAMPLE);

        ResponseEntity<GrowthMetricsResponse> response = controller.growth(null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
