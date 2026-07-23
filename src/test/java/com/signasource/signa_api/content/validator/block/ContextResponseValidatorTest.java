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

class ContextResponseValidatorTest {

    private ContextResponseValidator validator;
    private ValidationContext ctx;

    @BeforeEach
    void setUp() {
        validator = new ContextResponseValidator(new BlockConfigParser(new ObjectMapper()));
        ctx = new ValidationContext("topic-1", "lesson-1", 3);
    }

    @Test
    void shouldPassForValidBlock() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(
                block(config("¿Cómo se saluda?", "hola", List.of("hola", "chau"))), ctx, errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldFailWhenQuestionIsNull() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(null, "hola", List.of("hola", "chau"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #3: question is required");
    }

    @Test
    void shouldFailWhenQuestionIsBlank() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("  ", "hola", List.of("hola", "chau"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #3: question is required");
    }

    @Test
    void shouldFailWhenAnswerIsNull() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(
                block(configNoAnswer("¿Cómo se saluda?", List.of("hola", "chau"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #3: answer is required");
    }

    @Test
    void shouldFailWhenAnswerIsBlank() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(
                block(config("¿Cómo se saluda?", "  ", List.of("hola", "chau"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #3: answer is required");
    }

    @Test
    void shouldFailWhenOptionsIsNull() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(configNoOptions("¿Cómo se saluda?", "hola")), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains("Topic topic-1 > Lesson lesson-1 > Block #3: options is required");
    }

    @Test
    void shouldFailWhenOptionsHasFewerThanTwoElements() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config("¿Cómo se saluda?", "hola", List.of("hola"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #3: options must have at least 2 elements");
    }

    @Test
    void shouldFailWhenAnswerIsNotInOptions() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(
                block(config("¿Cómo se saluda?", "hola", List.of("chau", "gracias"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #3: answer must be one of the options");
    }

    @Test
    void shouldFailWhenConfigIsInvalidJson() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(JsonNodeFactory.instance.arrayNode()), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #3: invalid config for CONTEXT_RESPONSE block");
    }

    @Test
    void shouldAccumulateMultipleErrors() {
        List<ValidationError> errors = new ArrayList<>();
        validator.validate(block(config(null, null, List.of("solo"))), ctx, errors);
        assertThat(errors)
                .extracting(ValidationError::render)
                .contains(
                        "Topic topic-1 > Lesson lesson-1 > Block #3: question is required",
                        "Topic topic-1 > Lesson lesson-1 > Block #3: answer is required",
                        "Topic topic-1 > Lesson lesson-1 > Block #3: options must have at least 2 elements");
    }

    private LessonBlockDto block(JsonNode config) {
        return new LessonBlockDto(BlockType.CONTEXT_RESPONSE, null, config);
    }

    private ObjectNode config(String question, String answer, List<String> options) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        if (question != null) {
            node.put("question", question);
        }
        if (answer != null) {
            node.put("answer", answer);
        }
        node.set("options", mapper.valueToTree(options));
        return node;
    }

    private ObjectNode configNoAnswer(String question, List<String> options) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("question", question);
        node.set("options", mapper.valueToTree(options));
        return node;
    }

    private ObjectNode configNoOptions(String question, String answer) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("question", question);
        node.put("answer", answer);
        return node;
    }
}
