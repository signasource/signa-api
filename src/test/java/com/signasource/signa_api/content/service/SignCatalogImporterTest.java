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
import com.signasource.signa_api.learning.service.SignMediaStorage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    @Mock private SignMediaStorage mediaStorage;

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
        TopicYaml topic =
                new TopicYaml(new TopicDto("t1", "Topic", null, null, null), List.of(lesson));
        CourseYaml yaml =
                new CourseYaml(
                        new CourseMetadataDto("basic", "Basic", null, false, null, null, false, null),
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
        when(mediaStorage.exists("lsa/hola.glb")).thenReturn(true);
        when(mediaStorage.exists("lsa/por favor.glb")).thenReturn(true);

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
    void shouldSkipMeaningsAlreadyInCatalogWithSameCase() {
        LessonBlockDto block = block(1);
        LoadedCourse course = course(LSA, block);
        Sign existingHola =
                Sign.builder()
                        .id(UUID.randomUUID())
                        .meaning("hola")
                        .animationUrl("lsa/hola.glb")
                        .signLanguage(lsa)
                        .build();
        when(signLanguageRepository.findByCode(LSA)).thenReturn(Optional.of(lsa));
        when(extractor.extract(block)).thenReturn(List.of("hola", "chau"));
        when(signRepository.findByMeaningIgnoreCase("hola")).thenReturn(Optional.of(existingHola));
        when(signRepository.findByMeaningIgnoreCase("chau")).thenReturn(Optional.empty());
        when(mediaStorage.exists("lsa/hola.glb")).thenReturn(true);

        importer.importSigns(List.of(course));

        verify(signRepository).saveAll(signsCaptor.capture());
        List<Sign> saved = signsCaptor.getValue();
        assertEquals(1, saved.size());
        assertEquals("chau", saved.get(0).getMeaning());
    }

    @Test
    void shouldUpdateSignWhenMeaningCaseDiffers() {
        LessonBlockDto block = block(1);
        LoadedCourse course = course(LSA, block);
        Sign existing =
                Sign.builder()
                        .id(UUID.randomUUID())
                        .meaning("a")
                        .animationUrl("lsa/a.glb")
                        .signLanguage(lsa)
                        .build();
        when(signLanguageRepository.findByCode(LSA)).thenReturn(Optional.of(lsa));
        when(extractor.extract(block)).thenReturn(List.of("A"));
        when(signRepository.findByMeaningIgnoreCase("A")).thenReturn(Optional.of(existing));
        when(mediaStorage.exists("lsa/A.glb")).thenReturn(true);

        importer.importSigns(List.of(course));

        verify(signRepository).save(existing);
        assertEquals("A", existing.getMeaning());
        assertEquals("lsa/A.glb", existing.getAnimationUrl());
        verify(signRepository).saveAll(signsCaptor.capture());
        assertTrue(signsCaptor.getValue().isEmpty());
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

    @Test
    void shouldKeepAnAnimationUploadedFromThePanel() {
        LessonBlockDto block = block(1);
        LoadedCourse course = course(LSA, block);
        Sign existing =
                Sign.builder()
                        .id(UUID.randomUUID())
                        .meaning("hola")
                        .animationUrl("lsa/hola.mp4")
                        .signLanguage(lsa)
                        .build();
        when(signLanguageRepository.findByCode(LSA)).thenReturn(Optional.of(lsa));
        when(extractor.extract(block)).thenReturn(List.of("hola"));
        when(signRepository.findByMeaningIgnoreCase("hola")).thenReturn(Optional.of(existing));
        when(mediaStorage.exists("lsa/hola.glb")).thenReturn(false);
        when(mediaStorage.exists("lsa/hola.mp4")).thenReturn(true);

        importer.importSigns(List.of(course));

        assertEquals("lsa/hola.mp4", existing.getAnimationUrl());
        verify(signRepository, org.mockito.Mockito.never()).save(existing);
    }

    @Test
    void shouldCreateSignWithMp4KeyWhenGlbMissingAndMp4Exists() {
        LessonBlockDto block = block(1);
        LoadedCourse course = course(LSA, block);
        when(signLanguageRepository.findByCode(LSA)).thenReturn(Optional.of(lsa));
        when(extractor.extract(block)).thenReturn(List.of("hola"));
        when(mediaStorage.exists("lsa/hola.glb")).thenReturn(false);
        when(mediaStorage.exists("lsa/hola.mp4")).thenReturn(true);

        importer.importSigns(List.of(course));

        verify(signRepository).saveAll(signsCaptor.capture());
        assertEquals("lsa/hola.mp4", signsCaptor.getValue().get(0).getAnimationUrl());
    }

    @Test
    void shouldCreateSignWithExtensionlessKeyWhenNoFileExists() {
        LessonBlockDto block = block(1);
        LoadedCourse course = course(LSA, block);
        when(signLanguageRepository.findByCode(LSA)).thenReturn(Optional.of(lsa));
        when(extractor.extract(block)).thenReturn(List.of("hola"));

        importer.importSigns(List.of(course));

        verify(signRepository).saveAll(signsCaptor.capture());
        assertEquals("lsa/hola", signsCaptor.getValue().get(0).getAnimationUrl());
    }

    @Test
    void shouldFillAMissingAnimationKey() {
        LessonBlockDto block = block(1);
        LoadedCourse course = course(LSA, block);
        Sign existing =
                Sign.builder().id(UUID.randomUUID()).meaning("hola").signLanguage(lsa).build();
        when(signLanguageRepository.findByCode(LSA)).thenReturn(Optional.of(lsa));
        when(extractor.extract(block)).thenReturn(List.of("hola"));
        when(signRepository.findByMeaningIgnoreCase("hola")).thenReturn(Optional.of(existing));
        when(mediaStorage.exists("lsa/hola.glb")).thenReturn(true);

        importer.importSigns(List.of(course));

        assertEquals("lsa/hola.glb", existing.getAnimationUrl());
        verify(signRepository).save(existing);
    }

    @Test
    void shouldRefreshMediaKeysOfExistingSignsWithoutReadingContent() {
        Sign stale =
                Sign.builder()
                        .meaning("hola")
                        .animationUrl("lsa/hola.glb")
                        .signLanguage(lsa)
                        .build();
        Sign video =
                Sign.builder()
                        .meaning("chau")
                        .animationUrl("lsa/chau.glb")
                        .signLanguage(lsa)
                        .build();
        Sign uploaded =
                Sign.builder()
                        .meaning("gracias")
                        .animationUrl("lsa/custom/gracias.mp4")
                        .signLanguage(lsa)
                        .build();
        Sign unchanged =
                Sign.builder()
                        .meaning("mamá")
                        .animationUrl("lsa/mamá.glb")
                        .signLanguage(lsa)
                        .build();
        when(signRepository.findAll()).thenReturn(List.of(stale, video, uploaded, unchanged));
        when(mediaStorage.exists(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(mediaStorage.exists("lsa/chau.mp4")).thenReturn(true);
        when(mediaStorage.exists("lsa/chau_lateral.mp4")).thenReturn(true);
        when(mediaStorage.exists("lsa/mamá.glb")).thenReturn(true);

        int changed = importer.refreshMediaKeys();

        assertEquals(2, changed);
        assertEquals("lsa/hola", stale.getAnimationUrl());
        assertEquals("lsa/chau.mp4", video.getAnimationUrl());
        assertEquals("lsa/chau_lateral.mp4", video.getLateralAnimationUrl());
        assertEquals("lsa/custom/gracias.mp4", uploaded.getAnimationUrl());
        verify(signRepository).save(stale);
        verify(signRepository).save(video);
        verify(signRepository, never()).save(uploaded);
        verify(signRepository, never()).save(unchanged);
    }

    @Test
    void shouldMatchBucketFilesIgnoringCaseAndAccents() {
        Sign clinic = Sign.builder().meaning("clinica").signLanguage(lsa).build();
        Sign cafe = Sign.builder().meaning("café").signLanguage(lsa).build();
        Sign lateralOnly = Sign.builder().meaning("autorizacion").signLanguage(lsa).build();
        when(signRepository.findAll()).thenReturn(List.of(clinic, cafe, lateralOnly));
        when(mediaStorage.exists(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(mediaStorage.listKeys("lsa/"))
                .thenReturn(
                        List.of(
                                "lsa/Clínica.mp4",
                                "lsa/Café.glb",
                                "lsa/Autorización.mp4",
                                "lsa/Autorización_lateral.mp4",
                                "lsa/_history/Café-123.glb"));

        int changed = importer.refreshMediaKeys();

        assertEquals(3, changed);
        assertEquals("lsa/Clínica.mp4", clinic.getAnimationUrl());
        assertEquals("lsa/Café.glb", cafe.getAnimationUrl());
        assertEquals("lsa/Autorización.mp4", lateralOnly.getAnimationUrl());
        assertEquals("lsa/Autorización_lateral.mp4", lateralOnly.getLateralAnimationUrl());
    }

    @Test
    void shouldKeepAnAccentedKeyAlreadyResolvedFromTheBucket() {
        Sign resolved =
                Sign.builder()
                        .meaning("clinica")
                        .animationUrl("lsa/Clínica.mp4")
                        .signLanguage(lsa)
                        .build();
        when(signRepository.findAll()).thenReturn(List.of(resolved));
        when(mediaStorage.exists(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(mediaStorage.listKeys("lsa/")).thenReturn(List.of("lsa/Clínica.mp4"));

        assertEquals(0, importer.refreshMediaKeys());
        verify(signRepository, never()).save(resolved);
    }

    @Test
    void shouldNormalizeNames() {
        assertEquals("manana", SignCatalogImporter.normalize("Mañana"));
        assertEquals("de nada", SignCatalogImporter.normalize("  De-Nada "));
    }

    @Test
    void shouldResolveSignsWhoseFileIsNamedDifferently() {
        Sign boy = Sign.builder().meaning("niño").signLanguage(lsa).build();
        Sign tomorrow = Sign.builder().meaning("mañana").signLanguage(lsa).build();
        Sign morning = Sign.builder().meaning("la mañana").signLanguage(lsa).build();
        Sign today = Sign.builder().meaning("hoy").signLanguage(lsa).build();
        when(signRepository.findAll()).thenReturn(List.of(boy, tomorrow, morning, today));
        when(mediaStorage.exists(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(mediaStorage.listKeys("lsa/"))
                .thenReturn(
                        List.of(
                                "lsa/nene.mp4",
                                "lsa/mañana (día siguiente).mp4",
                                "lsa/mañana (parte del día).mp4",
                                "lsa/hoy-ahora.mp4"));

        assertEquals(4, importer.refreshMediaKeys());
        assertEquals("lsa/nene.mp4", boy.getAnimationUrl());
        assertEquals("lsa/mañana (día siguiente).mp4", tomorrow.getAnimationUrl());
        assertEquals("lsa/mañana (parte del día).mp4", morning.getAnimationUrl());
        assertEquals("lsa/hoy-ahora.mp4", today.getAnimationUrl());
        assertEquals(0, importer.refreshMediaKeys());
    }
}
