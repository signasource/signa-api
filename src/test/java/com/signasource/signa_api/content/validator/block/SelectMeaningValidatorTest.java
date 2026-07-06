package com.signasource.signa_api.content.validator.block;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.signasource.signa_api.content.dto.validation.ValidationContext;
import com.signasource.signa_api.content.dto.validation.ValidationError;
import com.signasource.signa_api.content.dto.yaml.LessonBlockDto;
import com.signasource.signa_api.content.util.BlockConfigParser;
import com.signasource.signa_api.learning.entity.BlockType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelectMeaningValidatorTest {

    private SelectMeaningValidator validator;
    private ValidationContext ctx;

    @BeforeEach
    void setUp() {
        validator = new SelectMeaningValidator(new BlockConfigParser(new ObjectMapper()));
        ctx = new ValidationContext("topic-1", "lesson-1", 2);
    }

    @Test
    void shouldPassForValidBlock() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("hola", List.of("hola", "chau"))), ctx, errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldFailWhenSignIsNull() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(null, List.of("hola", "chau"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #2: sign is required");
    }

    @Test
    void shouldFailWhenSignIsBlank() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("  ", List.of("hola", "chau"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #2: sign is required");
    }

    @Test
    void shouldFailWhenOptionsIsNull() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(configNullOptions("hola")), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #2: options is required");
    }

    @Test
    void shouldFailWhenOptionsHasFewerThanTwoElements() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("hola", List.of("hola"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #2: options must have at least 2 elements");
    }

    @Test
    void shouldFailWhenSignIsNotInOptions() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("hola", List.of("chau", "gracias"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #2: sign must be one of the options");
    }

    @Test
    void shouldFailWhenConfigIsInvalidJson() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.arrayNode()), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #2: invalid config for SELECT_MEANING block");
    }

    @Test
    void shouldAccumulateMultipleErrors() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(null, List.of("solo"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #2: sign is required",
                        "Topic topic-1 > Lesson lesson-1 > Block #2: options must have at least 2 elements");
    }

    private LessonBlockDto block(JsonNode config) {
        return new LessonBlockDto(BlockType.SELECT_MEANING, null, config);
    }

    private ObjectNode config(String sign, List<String> options) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        if (sign != null) {
            node.put("sign", sign);
        }
        node.set("options", mapper.valueToTree(options));
        return node;
    }

    private ObjectNode configNullOptions(String sign) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("sign", sign);
        return node;
    }
}
