package com.signasource.signa_api.content.validator.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.signasource.signa_api.content.dto.validation.ValidationContext;
import com.signasource.signa_api.content.dto.validation.ValidationError;
import com.signasource.signa_api.content.dto.yaml.LessonBlockDto;
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
        validator = new SpellNameValidator();
        ctx = new ValidationContext("topic-1", "lesson-1", 1);
    }

    @Test
    void shouldSupportSpellNameBlocks() {
        assertEquals(BlockType.SPELL_NAME, validator.supports());
    }

    @Test
    void shouldPassForAnyBlock() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(10)), ctx, errors);
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    void shouldPassWhenConfigIsOmitted() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.objectNode()), ctx, errors);
        assertTrue(errors.isEmpty(), errors.toString());
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
