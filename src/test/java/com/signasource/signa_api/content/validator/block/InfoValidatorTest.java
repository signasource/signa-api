package com.signasource.signa_api.content.validator.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void shouldPassWithTextOnly() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(infoConfig("Hello world")), ctx, errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldPassWithTitleOnly() {
        List<ValidationError> errors = new ArrayList<>();
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        config.put("title", "Introduction");
        validator.validate(block(config), ctx, errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldPassWithAllFields() {
        List<ValidationError> errors = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        config.put("title", "Myths");
        config.put("text", "Some text");
        ObjectNode myth = JsonNodeFactory.instance.objectNode();
        myth.put("title", "Myth title");
        myth.put("myth", "Myth statement");
        myth.put("reality", "Reality statement");
        config.set("myths", mapper.createArrayNode().add(myth));
        validator.validate(block(config), ctx, errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldPassWithEmptyConfig() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.objectNode()), ctx, errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldFailWhenConfigIsInvalidJson() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.arrayNode()), ctx, errors);
        assertEquals(1, errors.size());
        assertEquals(
                "Topic topic-1 > Lesson lesson-1 > Block #1: invalid config for INFO block",
                errors.get(0).render());
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
