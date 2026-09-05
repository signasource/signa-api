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

class InvisibleSignsValidatorTest {

    private InvisibleSignsValidator validator;
    private ValidationContext ctx;

    @BeforeEach
    void setUp() {
        validator = new InvisibleSignsValidator(new BlockConfigParser(new ObjectMapper()));
        ctx = new ValidationContext("topic-1", "lesson-1", 1);
    }

    @Test
    void shouldPassForValidBlock() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(List.of("a", "b", "c"))), ctx, errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldPassWithSingleSign() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(List.of("hola"))), ctx, errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldFailWhenSignsIsNull() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.objectNode()), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #1: signs must have at least 1 element");
    }

    @Test
    void shouldFailWhenSignsIsEmpty() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(List.of())), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #1: signs must have at least 1 element");
    }

    @Test
    void shouldFailWhenConfigIsInvalidJson() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.arrayNode()), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #1: invalid config for INVISIBLE_SIGNS block");
    }

    private LessonBlockDto block(JsonNode config) {
        return new LessonBlockDto(BlockType.INVISIBLE_SIGNS, null, config);
    }

    private ObjectNode config(List<String> signs) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.set("signs", mapper.valueToTree(signs));
        return node;
    }
}
