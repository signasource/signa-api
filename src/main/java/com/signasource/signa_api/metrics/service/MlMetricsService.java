package com.signasource.signa_api.metrics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.metrics.dto.MlMetricsResponse;
import com.signasource.signa_api.metrics.dto.MlMetricsResponse.SignRecognition;
import com.signasource.signa_api.metrics.repository.LearningMetricsRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds per-sign camera-recognition stats for the ML apartado of the usage dashboard. */
@Service
@RequiredArgsConstructor
public class MlMetricsService {

    private static final int SIGN_LIMIT = 50;

    private final LearningMetricsRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public MlMetricsResponse getMlMetrics(MetricsRange range) {
        List<Object[]> rows = repository.cameraBlockStats(range.start(), range.end());

        long totalPredictions = 0;
        Map<String, long[]> bySign = new LinkedHashMap<>();

        for (Object[] row : rows) {
            long evaluated = toLong(row[2]);
            long correct = toLong(row[3]);
            totalPredictions += evaluated;

            BlockType type = (BlockType) row[1];
            String config = row[4] != null ? String.valueOf(row[4]) : null;
            String sign = extractSign(config, type);
            if (sign == null || sign.isBlank()) continue;

            long[] slot = bySign.computeIfAbsent(sign, k -> new long[2]);
            slot[0] += evaluated;
            slot[1] += correct;
        }

        List<SignRecognition> recognizedSigns = new ArrayList<>(bySign.size());
        for (Map.Entry<String, long[]> e : bySign.entrySet()) {
            long[] v = e.getValue();
            recognizedSigns.add(new SignRecognition(e.getKey(), v[0], v[1], percent(v[1], v[0])));
        }
        recognizedSigns.sort(Comparator.comparingLong(SignRecognition::attempts).reversed());
        if (recognizedSigns.size() > SIGN_LIMIT) {
            recognizedSigns = recognizedSigns.subList(0, SIGN_LIMIT);
        }

        return new MlMetricsResponse(totalPredictions, recognizedSigns);
    }

    @SuppressWarnings("unchecked")
    private String extractSign(String config, BlockType type) {
        if (config == null || config.isBlank()) return null;
        try {
            Map<String, Object> map = objectMapper.readValue(config, Map.class);
            return switch (type) {
                case PERFORM_SIGN -> {
                    String meaning = (String) map.get("meaning");
                    yield meaning != null ? meaning : (String) map.get("word");
                }
                case SPELL_NAME -> (String) map.get("word");
                default -> null;
            };
        } catch (JsonProcessingException | RuntimeException e) {
            return null;
        }
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static double percent(long part, long whole) {
        return whole == 0 ? 0.0 : Math.round((part * 1000.0) / whole) / 10.0;
    }
}
