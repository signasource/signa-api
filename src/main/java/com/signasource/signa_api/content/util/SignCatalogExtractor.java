package com.signasource.signa_api.content.util;

import com.signasource.signa_api.content.dto.config.ContextResponseConfig;
import com.signasource.signa_api.content.dto.config.MatchConfig;
import com.signasource.signa_api.content.dto.config.SelectMeaningConfig;
import com.signasource.signa_api.content.dto.config.SelectSignConfig;
import com.signasource.signa_api.content.dto.config.VisualRecognitionConfig;
import com.signasource.signa_api.content.dto.yaml.LessonBlockDto;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SignCatalogExtractor {

    private final BlockConfigParser parser;

    public List<String> extract(LessonBlockDto block) {
        return switch (block.type()) {
            case INFO -> List.of();
            case SELECT_MEANING ->
                    parser.parse(block.config(), SelectMeaningConfig.class)
                            .map(config -> single(config.sign()))
                            .orElseGet(List::of);
            case SELECT_SIGN ->
                    parser.parse(block.config(), SelectSignConfig.class)
                            .map(config -> many(config.options()))
                            .orElseGet(List::of);
            case CONTEXT_RESPONSE ->
                    parser.parse(block.config(), ContextResponseConfig.class)
                            .map(config -> many(config.options()))
                            .orElseGet(List::of);
            case MATCH ->
                    parser.parse(block.config(), MatchConfig.class)
                            .map(config -> many(config.concepts()))
                            .orElseGet(List::of);
            case VISUAL_RECOGNITION ->
                    parser.parse(block.config(), VisualRecognitionConfig.class)
                            .map(config -> many(config.signSequence()))
                            .orElseGet(List::of);
        };
    }

    private List<String> single(String value) {
        if (value == null) {
            return List.of();
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? List.of() : List.of(stripped);
    }

    private List<String> many(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
