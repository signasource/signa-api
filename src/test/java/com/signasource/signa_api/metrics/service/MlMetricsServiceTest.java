package com.signasource.signa_api.metrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.metrics.dto.MlMetricsResponse;
import com.signasource.signa_api.metrics.repository.LearningMetricsRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MlMetricsServiceTest {

    @Mock private LearningMetricsRepository repository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private MlMetricsService service;

    private final MetricsRange range =
            MetricsRange.of(
                    Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-08T00:00:00Z"));

    @Test
    void shouldReturnEmptyWhenNoRows() {
        when(repository.cameraBlockStats(any(), any())).thenReturn(List.of());

        MlMetricsResponse result = service.getMlMetrics(range);

        assertEquals(0L, result.totalPredictions());
        assertEquals(List.of(), result.recognizedSigns());
    }

    @Test
    void shouldAggregatePredictionsAndSignsFromPerformSign() {
        when(repository.cameraBlockStats(any(), any()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {
                                    null,
                                    BlockType.PERFORM_SIGN,
                                    10L,
                                    7L,
                                    "{\"meaning\":\"hola\",\"word\":\"hola\"}"
                                },
                                new Object[] {
                                    null,
                                    BlockType.PERFORM_SIGN,
                                    5L,
                                    4L,
                                    "{\"meaning\":\"chau\",\"word\":\"chau\"}"
                                }));

        MlMetricsResponse result = service.getMlMetrics(range);

        assertEquals(15L, result.totalPredictions());
        assertEquals(2, result.recognizedSigns().size());
        // sorted descending by attempts
        assertEquals("hola", result.recognizedSigns().get(0).sign());
        assertEquals(10L, result.recognizedSigns().get(0).attempts());
        assertEquals(70.0, result.recognizedSigns().get(0).accuracyPercent());
    }

    @Test
    void shouldAggregateSpellName() {
        when(repository.cameraBlockStats(any(), any()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {
                                    null, BlockType.SPELL_NAME, 8L, 6L, "{\"word\":\"mamá\"}"
                                }));

        MlMetricsResponse result = service.getMlMetrics(range);

        assertEquals(8L, result.totalPredictions());
        assertEquals(1, result.recognizedSigns().size());
        assertEquals("mamá", result.recognizedSigns().get(0).sign());
        assertEquals(75.0, result.recognizedSigns().get(0).accuracyPercent());
    }

    @Test
    void shouldSkipRowsWithMissingConfig() {
        when(repository.cameraBlockStats(any(), any()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {null, BlockType.PERFORM_SIGN, 3L, 2L, null}));

        MlMetricsResponse result = service.getMlMetrics(range);

        assertEquals(3L, result.totalPredictions());
        assertEquals(List.of(), result.recognizedSigns());
    }

    @Test
    void shouldMergeAttemptsForSameSign() {
        when(repository.cameraBlockStats(any(), any()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {
                                    null, BlockType.PERFORM_SIGN, 4L, 3L, "{\"meaning\":\"hola\"}"
                                },
                                new Object[] {
                                    null, BlockType.SPELL_NAME, 6L, 5L, "{\"word\":\"hola\"}"
                                }));

        MlMetricsResponse result = service.getMlMetrics(range);

        assertEquals(10L, result.totalPredictions());
        assertEquals(1, result.recognizedSigns().size());
        assertEquals("hola", result.recognizedSigns().get(0).sign());
        assertEquals(10L, result.recognizedSigns().get(0).attempts());
        assertEquals(80.0, result.recognizedSigns().get(0).accuracyPercent());
    }
}
