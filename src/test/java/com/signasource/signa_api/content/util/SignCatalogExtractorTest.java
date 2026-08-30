package com.signasource.signa_api.content.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.TextNode;
import com.signasource.signa_api.content.dto.config.ContextResponseConfig;
import com.signasource.signa_api.content.dto.config.InfoConfig;
import com.signasource.signa_api.content.dto.config.MatchConfig;
import com.signasource.signa_api.content.dto.config.SelectMeaningConfig;
import com.signasource.signa_api.content.dto.config.SelectSignConfig;
import com.signasource.signa_api.content.dto.config.VisualRecognitionConfig;
import com.signasource.signa_api.content.dto.yaml.LessonBlockDto;
import com.signasource.signa_api.learning.entity.BlockType;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SignCatalogExtractorTest {

    private ObjectMapper mapper;
    private SignCatalogExtractor extractor;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        extractor = new SignCatalogExtractor(new BlockConfigParser(mapper));
    }

    private LessonBlockDto block(BlockType type, Object config) {
        return new LessonBlockDto(type, null, mapper.valueToTree(config));
    }

    @Test
    void shouldExtractSignFromSelectMeaning() {
        LessonBlockDto block =
                block(
                        BlockType.SELECT_MEANING,
                        new SelectMeaningConfig("hola", List.of("hola", "chau")));
        assertEquals(List.of("hola"), extractor.extract(block));
    }

    @Test
    void shouldExtractAllOptionsFromSelectSign() {
        LessonBlockDto block =
                block(BlockType.SELECT_SIGN, new SelectSignConfig("chau", List.of("hola", "chau")));
        assertEquals(List.of("hola", "chau"), extractor.extract(block));
    }

    @Test
    void shouldExtractOptionsAndSkipQuestionFromContextResponse() {
        LessonBlockDto block =
                block(
                        BlockType.CONTEXT_RESPONSE,
                        new ContextResponseConfig(
                                "¿Qué dirías si alguien te ayuda?",
                                "gracias",
                                List.of("hola", "gracias", "perdón", "chau")));
        assertEquals(List.of("hola", "gracias", "perdón", "chau"), extractor.extract(block));
    }

    @Test
    void shouldExtractAllConceptsFromMatch() {
        LessonBlockDto block =
                block(BlockType.MATCH, new MatchConfig(List.of("hola", "chau", "gracias")));
        assertEquals(List.of("hola", "chau", "gracias"), extractor.extract(block));
    }

    @Test
    void shouldExtractSignSequenceFromVisualRecognition() {
        LessonBlockDto block =
                block(
                        BlockType.VISUAL_RECOGNITION,
                        new VisualRecognitionConfig(
                                List.of("hola", "gracias"),
                                List.of("hola", "chau", "gracias"),
                                false));
        assertEquals(List.of("hola", "gracias"), extractor.extract(block));
    }

    @Test
    void shouldReturnEmptyForInfoBlock() {
        LessonBlockDto block = block(BlockType.INFO, new InfoConfig("Title", "Body", null));
        assertTrue(extractor.extract(block).isEmpty());
    }

    @Test
    void shouldStripBlanksAndDropNullsAndEmpties() {
        LessonBlockDto block =
                block(
                        BlockType.MATCH,
                        new MatchConfig(Arrays.asList("  hola  ", "", null, "chau")));
        assertEquals(List.of("hola", "chau"), extractor.extract(block));
    }

    @Test
    void shouldReturnEmptyWhenSingleFieldMissing() {
        LessonBlockDto block =
                block(BlockType.SELECT_MEANING, new SelectMeaningConfig(null, List.of("hola")));
        assertTrue(extractor.extract(block).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenArrayFieldMissing() {
        LessonBlockDto block = block(BlockType.MATCH, new MatchConfig(null));
        assertTrue(extractor.extract(block).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenConfigIsUnparseable() {
        LessonBlockDto block = new LessonBlockDto(BlockType.MATCH, null, TextNode.valueOf("nope"));
        assertTrue(extractor.extract(block).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenBlankSingleField() {
        JsonNode config = mapper.valueToTree(new SelectMeaningConfig("   ", List.of("hola")));
        LessonBlockDto block = new LessonBlockDto(BlockType.SELECT_MEANING, null, config);
        assertTrue(extractor.extract(block).isEmpty());
    }
}
