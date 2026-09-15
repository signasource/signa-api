package com.signasource.signa_api.content.validator.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

class PerformSignValidatorTest {

    private PerformSignValidator validator;
    private ValidationContext ctx;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper =
                new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        validator = new PerformSignValidator(new BlockConfigParser(mapper));
        ctx = new ValidationContext("topic-1", "lesson-1", 1);
    }

    @Test
    void shouldPassForValidBlock() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("mama", "papa", "casa")), ctx, errors);
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    void shouldFailWhenTheModelDoesNotKnowASign() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("mama", "bicicleta")), ctx, errors);
        assertEquals(1, errors.size());
        assertTrue(
                errors.get(0).render().contains("does not know the sign 'bicicleta'"),
                errors.get(0).render());
    }

    @Test
    void shouldFailWhenASignIsTheRestingClass() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("reposo")), ctx, errors);
        assertEquals(1, errors.size());
    }

    @Test
    void shouldAcceptSignsWithSurroundingSpacesAndUppercase() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("  Casa  ")), ctx, errors);
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    void shouldFailWhenSignsIsEmpty() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config()), ctx, errors);
        assertEquals(1, errors.size());
        assertEquals(
                "Topic topic-1 > Lesson lesson-1 > Block #1: signs must have at least 1 element",
                errors.get(0).render());
    }

    @Test
    void shouldFailWhenSignsIsMissing() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.objectNode()), ctx, errors);
        assertEquals(1, errors.size());
    }

    @Test
    void shouldFailWhenThresholdIsOutOfRange() {
        ObjectNode config = config("mama");
        config.put("threshold", 1.4);
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config), ctx, errors);
        assertEquals(1, errors.size());
        assertEquals(
                "Topic topic-1 > Lesson lesson-1 > Block #1: threshold must be between 0 and 1",
                errors.get(0).render());
    }

    @Test
    void shouldFailWhenConfigIsInvalidJson() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.arrayNode()), ctx, errors);
        assertEquals(1, errors.size());
        assertEquals(
                "Topic topic-1 > Lesson lesson-1 > Block #1: invalid config for PERFORM_SIGN block",
                errors.get(0).render());
    }

    private LessonBlockDto block(JsonNode config) {
        return new LessonBlockDto(BlockType.PERFORM_SIGN, null, config);
    }

    private ObjectNode config(String... signs) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        ArrayNode array = node.putArray("signs");
        for (String sign : signs) {
            array.add(sign);
        }
        return node;
    }
}
