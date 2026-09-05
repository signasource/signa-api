package com.signasource.signa_api.content.validator.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class IntroduceSignValidatorTest {

    private IntroduceSignValidator validator;
    private ValidationContext ctx;

    @BeforeEach
    void setUp() {
        validator = new IntroduceSignValidator(new BlockConfigParser(new ObjectMapper()));
        ctx = new ValidationContext("topic-1", "lesson-1", 1);
    }

    @Test
    void shouldPassForValidBlock() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("hola", "hola")), ctx, errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldFailWhenMeaningIsNull() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(null, "hola")), ctx, errors);
        assertEquals(1, errors.size());
        assertEquals(
                "Topic topic-1 > Lesson lesson-1 > Block #1: meaning is required",
                errors.get(0).render());
    }

    @Test
    void shouldFailWhenMeaningIsBlank() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("  ", "hola")), ctx, errors);
        assertEquals(1, errors.size());
        assertEquals(
                "Topic topic-1 > Lesson lesson-1 > Block #1: meaning is required",
                errors.get(0).render());
    }

    @Test
    void shouldFailWhenWordIsNull() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("hola", null)), ctx, errors);
        assertEquals(1, errors.size());
        assertEquals(
                "Topic topic-1 > Lesson lesson-1 > Block #1: word is required",
                errors.get(0).render());
    }

    @Test
    void shouldFailWhenWordIsBlank() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("hola", "  ")), ctx, errors);
        assertEquals(1, errors.size());
        assertEquals(
                "Topic topic-1 > Lesson lesson-1 > Block #1: word is required",
                errors.get(0).render());
    }

    @Test
    void shouldAccumulateErrorsForBothMissingFields() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(null, null)), ctx, errors);
        assertEquals(2, errors.size());
    }

    @Test
    void shouldFailWhenConfigIsInvalidJson() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.arrayNode()), ctx, errors);
        assertEquals(1, errors.size());
        assertEquals(
                "Topic topic-1 > Lesson lesson-1 > Block #1: invalid config for INTRODUCE_SIGN block",
                errors.get(0).render());
    }

    private LessonBlockDto block(com.fasterxml.jackson.databind.JsonNode config) {
        return new LessonBlockDto(BlockType.INTRODUCE_SIGN, null, config);
    }

    private ObjectNode config(String meaning, String word) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        if (meaning != null) {
            node.put("meaning", meaning);
        }
        if (word != null) {
            node.put("word", word);
        }
        return node;
    }
}
