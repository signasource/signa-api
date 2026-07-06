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

class SelectSignValidatorTest {

    private SelectSignValidator validator;
    private ValidationContext ctx;

    @BeforeEach
    void setUp() {
        validator = new SelectSignValidator(new BlockConfigParser(new ObjectMapper()));
        ctx = new ValidationContext("topic-1", "lesson-1", 3);
    }

    @Test
    void shouldPassForValidBlock() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("hola", List.of("hola", "chau"))), ctx, errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldFailWhenWordIsNull() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(null, List.of("hola", "chau"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #3: word is required");
    }

    @Test
    void shouldFailWhenWordIsBlank() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("  ", List.of("hola", "chau"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #3: word is required");
    }

    @Test
    void shouldFailWhenOptionsIsNull() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(configNullOptions("hola")), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #3: options is required");
    }

    @Test
    void shouldFailWhenOptionsHasFewerThanTwoElements() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("hola", List.of("hola"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #3: options must have at least 2 elements");
    }

    @Test
    void shouldFailWhenWordIsNotInOptions() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("hola", List.of("chau", "gracias"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #3: word must be one of the options");
    }

    @Test
    void shouldFailWhenConfigIsInvalidJson() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.arrayNode()), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #3: invalid config for SELECT_SIGN block");
    }

    private LessonBlockDto block(JsonNode config) {
        return new LessonBlockDto(BlockType.SELECT_SIGN, null, config);
    }

    private ObjectNode config(String word, List<String> options) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        if (word != null) {
            node.put("word", word);
        }
        node.set("options", mapper.valueToTree(options));
        return node;
    }

    private ObjectNode configNullOptions(String word) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("word", word);
        return node;
    }
}
