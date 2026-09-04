package com.signasource.signa_api.learning.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.LessonBlock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlockSignExtractorTest {

    private BlockSignExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new BlockSignExtractor(new ObjectMapper());
    }

    private LessonBlock block(BlockType type, String config) {
        return LessonBlock.builder().type(type).config(config).build();
    }

    @Test
    void shouldExtractSignFromSelectMeaning() {
        LessonBlock block =
                block(
                        BlockType.SELECT_MEANING,
                        "{\"sign\":\"hola\",\"options\":[\"hola\",\"chau\"]}");
        assertEquals(List.of("hola"), extractor.extract(block));
    }

    @Test
    void shouldExtractWordFromSelectSign() {
        LessonBlock block =
                block(
                        BlockType.SELECT_SIGN,
                        "{\"word\":\"por_favor\",\"options\":[\"hola\",\"por_favor\"]}");
        assertEquals(List.of("por_favor"), extractor.extract(block));
    }

    @Test
    void shouldExtractAllConceptsFromMatch() {
        LessonBlock block =
                block(BlockType.MATCH, "{\"concepts\":[\"hola\",\"chau\",\"gracias\"]}");
        assertEquals(List.of("hola", "chau", "gracias"), extractor.extract(block));
    }

    @Test
    void shouldExtractAnswerFromContextResponse() {
        LessonBlock block =
                block(
                        BlockType.CONTEXT_RESPONSE,
                        "{\"question\":\"How do you say hello?\",\"answer\":\"hola\",\"options\":[\"hola\",\"chau\"]}");
        assertEquals(List.of("hola"), extractor.extract(block));
    }

    @Test
    void shouldExtractSignSequenceFromVisualRecognition() {
        LessonBlock block =
                block(
                        BlockType.VISUAL_RECOGNITION,
                        "{\"sign_sequence\":[\"a\",\"b\"],\"options\":[\"ab\",\"ba\"],\"keep_order\":true}");
        assertEquals(List.of("a", "b"), extractor.extract(block));
    }

    @Test
    void shouldReturnEmptyForInfoBlock() {
        LessonBlock block = block(BlockType.INFO, "{\"text\":\"Hello\"}");
        assertTrue(extractor.extract(block).isEmpty());
    }

    @Test
    void shouldReturnEmptyOnMalformedConfig() {
        LessonBlock block = block(BlockType.SELECT_MEANING, "not-json");
        assertTrue(extractor.extract(block).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenFieldMissing() {
        LessonBlock block = block(BlockType.SELECT_MEANING, "{\"options\":[\"hola\"]}");
        assertTrue(extractor.extract(block).isEmpty());
    }
}
