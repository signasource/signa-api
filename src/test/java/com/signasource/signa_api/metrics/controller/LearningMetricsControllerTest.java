package com.signasource.signa_api.metrics.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.metrics.dto.LearningMetricsResponse;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.ReportsSection;
import com.signasource.signa_api.metrics.service.LearningMetricsService;
import com.signasource.signa_api.metrics.service.MetricsRange;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class LearningMetricsControllerTest {

    @Mock private LearningMetricsService service;
    @InjectMocks private LearningMetricsController controller;

    private static final LearningMetricsResponse SAMPLE =
            new LearningMetricsResponse(
                    List.of(),
                    List.of(),
                    List.of(),
                    0,
                    List.of(),
                    new ReportsSection(0, 0, List.of()),
                    List.of(),
                    new LearningMetricsResponse.LessonFlow(0, 0, 0),
                    new LearningMetricsResponse.ExerciseFlow(0, 0, 0, 0),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());

    @Test
    void returnsTheSnapshotForTheRequestedWindow() {
        ArgumentCaptor<MetricsRange> range = ArgumentCaptor.forClass(MetricsRange.class);
        when(service.getLearningMetrics(range.capture(), any())).thenReturn(SAMPLE);

        ResponseEntity<LearningMetricsResponse> response =
                controller.learning("2026-11-01T00:00:00Z", "2026-11-08T00:00:00Z", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(SAMPLE, response.getBody());
        verify(service).getLearningMetrics(range.getValue(), List.of());
    }

    @Test
    void treatsMissingParamsAsTheWholeHistory() {
        when(service.getLearningMetrics(MetricsRange.ALL_TIME, List.<UUID>of())).thenReturn(SAMPLE);

        ResponseEntity<LearningMetricsResponse> response = controller.learning(null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
