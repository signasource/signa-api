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

class ContextValidatorTest {

    private ContextValidator validator;
    private ValidationContext ctx;

    @BeforeEach
    void setUp() {
        validator = new ContextValidator(new BlockConfigParser(new ObjectMapper()));
        ctx = new ValidationContext("topic-1", "lesson-1", 2);
    }

    @Test
    void shouldPassForValidBlock() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(
                block(config("¡_ por ayudarme!", "gracias", List.of("hola", "gracias", "chau"))),
                ctx,
                errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldFailWhenSentenceIsNull() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(null, "gracias", List.of("hola", "gracias"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #2: sentence is required");
    }

    @Test
    void shouldFailWhenSentenceIsBlank() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("  ", "gracias", List.of("hola", "gracias"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #2: sentence is required");
    }

    @Test
    void shouldFailWhenAnswerIsNull() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("¡_!", null, List.of("hola", "gracias"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #2: answer is required");
    }

    @Test
    void shouldFailWhenAnswerIsBlank() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("¡_!", "  ", List.of("hola", "gracias"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #2: answer is required");
    }

    @Test
    void shouldFailWhenOptionsIsNull() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(configNullOptions("¡_!", "gracias")), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #2: options is required");
    }

    @Test
    void shouldFailWhenOptionsHasFewerThanTwoElements() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("¡_!", "gracias", List.of("gracias"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #2: options must have at least 2 elements");
    }

    @Test
    void shouldFailWhenAnswerIsNotInOptions() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("¡_!", "gracias", List.of("hola", "chau"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #2: answer must be one of the options");
    }

    @Test
    void shouldFailWhenConfigIsInvalidJson() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.arrayNode()), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #2: invalid config for CONTEXT block");
    }

    @Test
    void shouldAccumulateMultipleErrors() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(null, null, List.of("solo"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #2: sentence is required",
                        "Topic topic-1 > Lesson lesson-1 > Block #2: answer is required",
                        "Topic topic-1 > Lesson lesson-1 > Block #2: options must have at least 2 elements");
    }

    private LessonBlockDto block(JsonNode config) {
        return new LessonBlockDto(BlockType.CONTEXT, null, config);
    }

    private ObjectNode config(String sentence, String answer, List<String> options) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        if (sentence != null) {
            node.put("sentence", sentence);
        }
        if (answer != null) {
            node.put("answer", answer);
        }
        node.set("options", mapper.valueToTree(options));
        return node;
    }

    private ObjectNode configNullOptions(String sentence, String answer) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("sentence", sentence);
        node.put("answer", answer);
        return node;
    }
}
