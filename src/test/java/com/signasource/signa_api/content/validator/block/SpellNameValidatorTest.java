package com.signasource.signa_api.content.validator.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
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

class SpellNameValidatorTest {

    private SpellNameValidator validator;
    private ValidationContext ctx;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper =
                new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        validator = new SpellNameValidator(new BlockConfigParser(mapper));
        ctx = new ValidationContext("topic-1", "lesson-1", 1);
    }

    @Test
    void shouldSupportSpellNameBlocks() {
        assertEquals(BlockType.SPELL_NAME, validator.supports());
    }

    @Test
    void shouldPassForValidBlock() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(10)), ctx, errors);
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    void shouldPassWhenMaxLettersIsOmitted() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.objectNode()), ctx, errors);
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    void shouldRejectMaxLettersAboveTheCap() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(13)), ctx, errors);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).message().contains("max_letters"), errors.toString());
    }

    @Test
    void shouldRejectMaxLettersBelowOne() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(0)), ctx, errors);
        assertEquals(1, errors.size());
    }

    @Test
    void shouldRejectConfigOfTheWrongShape() {
        List<ValidationError> errors = new ArrayList<>();
        ObjectNode wrong = JsonNodeFactory.instance.objectNode();
        wrong.put("max_letters", "muchas");
        validator.validate(block(wrong), ctx, errors);
        assertEquals(1, errors.size());
    }

    private LessonBlockDto block(JsonNode config) {
        return new LessonBlockDto(BlockType.SPELL_NAME, null, config);
    }

    private JsonNode config(int maxLetters) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("max_letters", maxLetters);
        return node;
    }
}
