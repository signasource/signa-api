package com.signasource.signa_api.metrics.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse;
import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse.AchievementsSection;
import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse.GiftsSection;
import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse.ShopSection;
import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse.StreaksSection;
import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse.XpSection;
import com.signasource.signa_api.metrics.service.GamificationMetricsService;
import com.signasource.signa_api.metrics.service.MetricsRange;
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
class GamificationMetricsControllerTest {

    @Mock private GamificationMetricsService service;
    @InjectMocks private GamificationMetricsController controller;

    private static final GamificationMetricsResponse SAMPLE =
            new GamificationMetricsResponse(
                    new XpSection(0, 0, 0, List.of(), null),
                    new StreaksSection(0, 0, 0, 0, 0),
                    new AchievementsSection(0, 0, List.of(), List.of(), List.of(), null),
                    List.of(),
                    new GiftsSection(0, 0),
                    new ShopSection(0, 0, 0, List.of(), List.of()),
                    List.of());

    @Test
    void returnsTheSnapshotForTheRequestedWindow() {
        ArgumentCaptor<MetricsRange> range = ArgumentCaptor.forClass(MetricsRange.class);
        when(service.getGamificationMetrics(range.capture())).thenReturn(SAMPLE);

        ResponseEntity<GamificationMetricsResponse> response =
                controller.gamification("2026-11-01T00:00:00Z", "2026-11-08T00:00:00Z");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(SAMPLE, response.getBody());
        verify(service).getGamificationMetrics(range.getValue());
    }

    @Test
    void treatsMissingParamsAsTheWholeHistory() {
        when(service.getGamificationMetrics(MetricsRange.ALL_TIME)).thenReturn(SAMPLE);

        ResponseEntity<GamificationMetricsResponse> response = controller.gamification(null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
