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

class VisualRecognitionValidatorTest {

    private VisualRecognitionValidator validator;
    private ValidationContext ctx;

    @BeforeEach
    void setUp() {
        validator = new VisualRecognitionValidator(new BlockConfigParser(new ObjectMapper()));
        ctx = new ValidationContext("topic-1", "lesson-1", 4);
    }

    @Test
    void shouldPassForValidBlock() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(
                block(config(List.of("hola", "chau"), List.of("hola", "chau", "gracias"), true)),
                ctx,
                errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldPassWithKeepOrderFalse() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(
                block(config(List.of("hola"), List.of("hola", "chau"), false)), ctx, errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldFailWhenSignSequenceIsNull() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(configNoSignSequence(List.of("hola", "chau"), true)), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #4: signSequence is required");
    }

    @Test
    void shouldFailWhenSignSequenceIsEmpty() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(List.of(), List.of("hola", "chau"), true)), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #4: signSequence must have at least 1 element");
    }

    @Test
    void shouldFailWhenOptionsIsNull() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(configNoOptions(List.of("hola"), true)), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #4: options is required");
    }

    @Test
    void shouldFailWhenOptionsIsEmpty() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(List.of("hola"), List.of(), true)), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #4: options must have at least 1 element");
    }

    @Test
    void shouldFailWhenKeepOrderIsAbsent() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(
                block(configNoKeepOrder(List.of("hola"), List.of("hola", "chau"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #4: keepOrder is required");
    }

    @Test
    void shouldFailWhenConfigIsInvalidJson() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.arrayNode()), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #4: invalid config for VISUAL_RECOGNITION block");
    }

    @Test
    void shouldAccumulateMultipleErrors() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(configNoSignSequence(List.of(), false)), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #4: signSequence is required",
                        "Topic topic-1 > Lesson lesson-1 > Block #4: options must have at least 1 element");
    }

    private LessonBlockDto block(JsonNode config) {
        return new LessonBlockDto(BlockType.VISUAL_RECOGNITION, null, config);
    }

    private ObjectNode config(List<String> signSequence, List<String> options, boolean keepOrder) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.set("signSequence", mapper.valueToTree(signSequence));
        node.set("options", mapper.valueToTree(options));
        node.put("keepOrder", keepOrder);
        return node;
    }

    private ObjectNode configNoSignSequence(List<String> options, boolean keepOrder) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.set("options", mapper.valueToTree(options));
        node.put("keepOrder", keepOrder);
        return node;
    }

    private ObjectNode configNoOptions(List<String> signSequence, boolean keepOrder) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.set("signSequence", mapper.valueToTree(signSequence));
        node.put("keepOrder", keepOrder);
        return node;
    }

    private ObjectNode configNoKeepOrder(List<String> signSequence, List<String> options) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.set("signSequence", mapper.valueToTree(signSequence));
        node.set("options", mapper.valueToTree(options));
        return node;
    }
}
