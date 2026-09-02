package com.signasource.signa_api.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.content.dto.load.LoadedCourse;
import com.signasource.signa_api.content.dto.yaml.CourseMetadataDto;
import com.signasource.signa_api.content.dto.yaml.CourseYaml;
import com.signasource.signa_api.content.dto.yaml.LessonBlockDto;
import com.signasource.signa_api.content.dto.yaml.LessonDto;
import com.signasource.signa_api.content.dto.yaml.TopicDto;
import com.signasource.signa_api.content.dto.yaml.TopicYaml;
import com.signasource.signa_api.content.exception.SignLanguageNotFoundException;
import com.signasource.signa_api.content.util.SignCatalogExtractor;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.Handedness;
import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignCatalogImporterTest {

    private static final String LSA = "LSA";

    @Mock private SignCatalogExtractor extractor;
    @Mock private SignRepository signRepository;
    @Mock private SignLanguageRepository signLanguageRepository;

    @InjectMocks private SignCatalogImporter importer;

    @Captor private ArgumentCaptor<List<Sign>> signsCaptor;

    private SignLanguage lsa;

    @BeforeEach
    void setUp() {
        lsa = SignLanguage.builder().code(LSA).name("Lengua de Señas Argentina").build();
    }

    private LessonBlockDto block(int discriminator) {
        return new LessonBlockDto(BlockType.MATCH, discriminator, null);
    }

    private LoadedCourse course(String langCode, LessonBlockDto... blocks) {
        LessonDto lesson = new LessonDto("l1", "Lesson", null, List.of(blocks));
        TopicYaml topic = new TopicYaml(new TopicDto("t1", "Topic", null, null), List.of(lesson));
        CourseYaml yaml =
                new CourseYaml(
                        new CourseMetadataDto("basic", "Basic", null, false, null),
                        null,
                        List.of());
        return new LoadedCourse(langCode, yaml, List.of(topic));
    }

    @Test
    void shouldCreateMissingSignsWithKeyHandednessAndLanguage() {
        LessonBlockDto block = block(1);
        LoadedCourse course = course(LSA, block);
        when(signLanguageRepository.findByCode(LSA)).thenReturn(Optional.of(lsa));
        when(extractor.extract(block)).thenReturn(List.of("hola", "por favor"));

        importer.importSigns(List.of(course));

        verify(signRepository).saveAll(signsCaptor.capture());
        List<Sign> saved = signsCaptor.getValue();
        assertEquals(2, saved.size());

        Sign hola = saved.get(0);
        assertEquals("hola", hola.getMeaning());
        assertEquals(Handedness.ONE_HANDED, hola.getHandedness());
        assertEquals("lsa/hola.glb", hola.getAnimationUrl());
        assertSame(lsa, hola.getSignLanguage());

        Sign porFavor = saved.get(1);
        assertEquals("por favor", porFavor.getMeaning());
        assertEquals("lsa/por favor.glb", porFavor.getAnimationUrl());
    }

    @Test
    void shouldSkipMeaningsAlreadyInCatalog() {
        LessonBlockDto block = block(1);
        LoadedCourse course = course(LSA, block);
        when(signLanguageRepository.findByCode(LSA)).thenReturn(Optional.of(lsa));
        when(extractor.extract(block)).thenReturn(List.of("hola", "chau"));
        when(signRepository.existsByMeaning("hola")).thenReturn(true);

        importer.importSigns(List.of(course));

        verify(signRepository).saveAll(signsCaptor.capture());
        List<Sign> saved = signsCaptor.getValue();
        assertEquals(1, saved.size());
        assertEquals("chau", saved.get(0).getMeaning());
    }

    @Test
    void shouldDeduplicateMeaningsAcrossBlocks() {
        LessonBlockDto first = block(1);
        LessonBlockDto second = block(2);
        LoadedCourse course = course(LSA, first, second);
        when(signLanguageRepository.findByCode(LSA)).thenReturn(Optional.of(lsa));
        when(extractor.extract(first)).thenReturn(List.of("hola", "chau"));
        when(extractor.extract(second)).thenReturn(List.of("chau", "gracias"));

        importer.importSigns(List.of(course));

        verify(signRepository).saveAll(signsCaptor.capture());
        assertEquals(
                List.of("hola", "chau", "gracias"),
                signsCaptor.getValue().stream().map(Sign::getMeaning).toList());
    }

    @Test
    void shouldSaveNothingWhenContentHasNoMeanings() {
        LessonBlockDto block = block(1);
        LoadedCourse course = course(LSA, block);
        when(signLanguageRepository.findByCode(LSA)).thenReturn(Optional.of(lsa));
        when(extractor.extract(block)).thenReturn(List.of());

        importer.importSigns(List.of(course));

        verify(signRepository).saveAll(signsCaptor.capture());
        assertTrue(signsCaptor.getValue().isEmpty());
    }

    @Test
    void shouldThrowWhenSignLanguageMissing() {
        LoadedCourse course = course(LSA, block(1));
        when(signLanguageRepository.findByCode(LSA)).thenReturn(Optional.empty());

        assertThrows(
                SignLanguageNotFoundException.class, () -> importer.importSigns(List.of(course)));

        verify(signRepository, never()).saveAll(any());
    }
}
