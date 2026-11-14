package com.signasource.signa_api.learning.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.LessonBlock;
import org.junit.jupiter.api.Test;

class SignReferencesTest {

    private final SignReferences refs = new SignReferences(new ObjectMapper());

    private static LessonBlock block(BlockType type, String config) {
        return LessonBlock.builder().type(type).config(config).build();
    }

    @Test
    void findsMeaningAnywhereInConfigIgnoringCase() {
        LessonBlock block =
                block(
                        BlockType.SELECT_MEANING,
                        "{\"sign\":\"Hola\",\"options\":[\"hola\",\"chau\",\"gracias\"]}");

        assertTrue(refs.references(block, "hola"));
        assertTrue(refs.references(block, " CHAU "));
        assertFalse(refs.references(block, "buen día"));
    }

    @Test
    void ignoresBlocksThatNameModelLabelsOrFreeText() {
        assertFalse(refs.references(block(BlockType.PERFORM_SIGN, "{\"sign\":\"hola\"}"), "hola"));
        assertFalse(refs.references(block(BlockType.INFO, "{\"title\":\"hola\"}"), "hola"));
        assertNull(refs.rename(block(BlockType.PERFORM_SIGN, "{\"sign\":\"hola\"}"), "hola", "x"));
    }

    @Test
    void renameRewritesEveryExactValueInNestedArraysAndObjects() {
        LessonBlock block =
                block(
                        BlockType.MATCH,
                        "{\"concepts\":[\"hola\",\"chau\"],\"extra\":{\"answer\":\"HOLA\"},"
                                + "\"question\":\"¿Cómo se dice hola?\"}");

        String renamed = refs.rename(block, "hola", "saludo");

        assertEquals(
                "{\"concepts\":[\"saludo\",\"chau\"],\"extra\":{\"answer\":\"saludo\"},"
                        + "\"question\":\"¿Cómo se dice hola?\"}",
                renamed);
    }

    @Test
    void renameReturnsNullWhenNothingMatchesOrConfigIsBroken() {
        assertNull(refs.rename(block(BlockType.MATCH, "{\"concepts\":[\"chau\"]}"), "hola", "x"));
        assertNull(refs.rename(block(BlockType.MATCH, "not json"), "hola", "x"));
        assertNull(refs.rename(block(BlockType.MATCH, null), "hola", "x"));
    }
}
