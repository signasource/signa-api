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

class InfoValidatorTest {

    private InfoValidator validator;
    private ValidationContext ctx;

    @BeforeEach
    void setUp() {
        validator = new InfoValidator(new BlockConfigParser(new ObjectMapper()));
        ctx = new ValidationContext("topic-1", "lesson-1", 1);
    }

    @Test
    void shouldPassForValidInfoBlock() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(infoConfig("Hello world")), ctx, errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldFailWhenTextIsNull() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.objectNode()), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #1: text is required");
    }

    @Test
    void shouldFailWhenTextIsBlank() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(infoConfig("   ")), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #1: text is required");
    }

    @Test
    void shouldFailWhenConfigIsInvalidJson() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.arrayNode()), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #1: invalid config for INFO block");
    }

    private LessonBlockDto block(JsonNode config) {
        return new LessonBlockDto(BlockType.INFO, null, config);
    }

    private ObjectNode infoConfig(String text) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("text", text);
        return node;
    }
}
