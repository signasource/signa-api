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

class MatchValidatorTest {

    private MatchValidator validator;
    private ValidationContext ctx;

    @BeforeEach
    void setUp() {
        validator = new MatchValidator(new BlockConfigParser(new ObjectMapper()));
        ctx = new ValidationContext("topic-1", "lesson-1", 1);
    }

    @Test
    void shouldPassForValidBlock() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(List.of("hola", "chau"))), ctx, errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldPassWithMoreThanTwoConcepts() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(List.of("hola", "chau", "gracias"))), ctx, errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldFailWhenConceptsIsNull() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.objectNode()), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #1: concepts is required");
    }

    @Test
    void shouldFailWhenConceptsHasFewerThanTwoElements() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(List.of("hola"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #1: concepts must have at least 2 elements");
    }

    @Test
    void shouldFailWhenConceptsIsEmpty() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(List.of())), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #1: concepts must have at least 2 elements");
    }

    @Test
    void shouldFailWhenConfigIsInvalidJson() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.arrayNode()), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #1: invalid config for MATCH block");
    }

    private LessonBlockDto block(JsonNode config) {
        return new LessonBlockDto(BlockType.MATCH, null, config);
    }

    private ObjectNode config(List<String> concepts) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.set("concepts", mapper.valueToTree(concepts));
        return node;
    }
}
