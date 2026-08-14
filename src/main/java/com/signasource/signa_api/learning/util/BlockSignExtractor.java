package com.signasource.signa_api.learning.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.learning.entity.LessonBlock;
import java.util.List;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;

@Component
public class BlockSignExtractor {

    private final ObjectMapper objectMapper;

    public BlockSignExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> extract(LessonBlock block) {
        return switch (block.getType()) {
            case INFO -> List.of();
            default -> {
                try {
                    JsonNode config = objectMapper.readTree(block.getConfig());
                    yield extractFromConfig(block.getType(), config);
                } catch (JsonProcessingException e) {
                    yield List.of();
                }
            }
        };
    }

    private List<String> extractFromConfig(
            com.signasource.signa_api.learning.entity.BlockType type, JsonNode config) {
        return switch (type) {
            case SELECT_MEANING -> singleText(config, "sign");
            case SELECT_SIGN -> singleText(config, "word");
            case CONTEXT_RESPONSE -> singleText(config, "answer");
            case MATCH -> arrayTexts(config, "concepts");
            case VISUAL_RECOGNITION -> arrayTexts(config, "signSequence");
            case INFO -> List.of();
        };
    }

    private List<String> singleText(JsonNode config, String field) {
        JsonNode node = config.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        String text = node.asText().strip();
        return text.isEmpty() ? List.of() : List.of(text);
    }

    private List<String> arrayTexts(JsonNode config, String field) {
        JsonNode array = config.path(field);
        if (!array.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(array.spliterator(), false)
                .map(n -> n.asText().strip())
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
