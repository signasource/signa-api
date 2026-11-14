package com.signasource.signa_api.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.admin.dto.AdminSignResponse;
import com.signasource.signa_api.admin.dto.CreateAdminSignRequest;
import com.signasource.signa_api.admin.dto.UpdateSignRequest;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.gamification.repository.UserLearnedSignRepository;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Handedness;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.SignReportRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
import com.signasource.signa_api.learning.service.SignMediaStorage;
import com.signasource.signa_api.learning.util.SignReferences;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSignServiceTest {

    private static final byte[] GLB = "glTF\u0002\0\0\0\0\0\0\0data".getBytes();
    private static final byte[] MP4 = "\0\0\0\u0018ftypisom\0\0\0\0data".getBytes();
    private static final byte[] MOV = "\0\0\0\u0014ftypqt  \0\0\0\0data".getBytes();

    @Mock private SignRepository signRepository;
    @Mock private SignLanguageRepository signLanguageRepository;
    @Mock private SignReportRepository signReportRepository;
    @Mock private LessonBlockRepository lessonBlockRepository;
    @Mock private UserLearnedSignRepository userLearnedSignRepository;
    @Mock private SignMediaStorage mediaStorage;

    private AdminSignService service;
    private SignLanguage lsa;
    private Sign hola;

    @BeforeEach
    void setUp() {
        service =
                new AdminSignService(
                        signRepository,
                        signLanguageRepository,
                        signReportRepository,
                        lessonBlockRepository,
                        userLearnedSignRepository,
                        new SignReferences(new ObjectMapper()),
                        mediaStorage);
        lsa = SignLanguage.builder().id(UUID.randomUUID()).code("LSA").name("LSA").build();
        hola =
                Sign.builder()
                        .id(UUID.randomUUID())
                        .meaning("hola")
                        .handedness(Handedness.ONE_HANDED)
                        .animationUrl("lsa/hola.glb")
                        .signLanguage(lsa)
                        .build();
        when(signRepository.findById(hola.getId())).thenReturn(Optional.of(hola));
        when(signRepository.save(any(Sign.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mediaStorage.publicUrl(anyString(), any()))
                .thenAnswer(inv -> "url:" + inv.getArgument(0));
        when(lessonBlockRepository.findWithCourseByTypeIn(any())).thenReturn(List.of());
    }

    private static LessonBlock block(VersionStatus status, BlockType type, String config) {
        Course course = Course.builder().id(UUID.randomUUID()).name("Básico").build();
        CourseVersion version =
                CourseVersion.builder()
                        .id(UUID.randomUUID())
                        .version("1")
                        .status(status)
                        .course(course)
                        .build();
        Topic topic =
                Topic.builder()
                        .id(UUID.randomUUID())
                        .title("Unidad 1")
                        .courseVersion(version)
                        .build();
        Lesson lesson = Lesson.builder().id(UUID.randomUUID()).name("Saludos").topic(topic).build();
        return LessonBlock.builder()
                .id(UUID.randomUUID())
                .type(type)
                .config(config)
                .lesson(lesson)
                .build();
    }

    @Test
    void listCountsLessonsInActiveVersionsOnly() {
        when(signRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(hola)));
        when(lessonBlockRepository.findWithCourseByTypeIn(any()))
                .thenReturn(
                        List.of(
                                block(
                                        VersionStatus.PUBLISHED,
                                        BlockType.INTRODUCE_SIGN,
                                        "{\"meaning\":\"Hola\"}"),
                                block(
                                        VersionStatus.DRAFT,
                                        BlockType.MATCH,
                                        "{\"concepts\":[\"hola\"]}"),
                                block(
                                        VersionStatus.ARCHIVED,
                                        BlockType.MATCH,
                                        "{\"concepts\":[\"hola\"]}")));

        Page<AdminSignResponse> page = service.list(null, Pageable.unpaged());

        assertEquals(2, page.getContent().get(0).lessonCount());
        assertEquals("glb", page.getContent().get(0).animationFormat());
    }

    @Test
    void detailListsUsagesAndMediaUrl() {
        when(lessonBlockRepository.findWithCourseByTypeIn(any()))
                .thenReturn(
                        List.of(
                                block(
                                        VersionStatus.PUBLISHED,
                                        BlockType.SELECT_MEANING,
                                        "{\"sign\":\"hola\"}")));

        var detail = service.detail(hola.getId());

        assertEquals("url:lsa/hola.glb", detail.mediaUrl());
        assertEquals(1, detail.usages().size());
        assertEquals("Saludos", detail.usages().get(0).lessonName());
        assertEquals(List.of("SELECT_MEANING"), detail.usages().get(0).blockTypes());
    }

    @Test
    void detailThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(signRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.detail(id));
    }

    @Test
    void renameCascadesToBlocksLearnedSignsAndCopiesTheDefaultFile() {
        LessonBlock published =
                block(
                        VersionStatus.PUBLISHED,
                        BlockType.SELECT_MEANING,
                        "{\"sign\":\"hola\",\"options\":[\"hola\",\"chau\"]}");
        LessonBlock unrelated =
                block(VersionStatus.DRAFT, BlockType.MATCH, "{\"concepts\":[\"chau\"]}");
        when(lessonBlockRepository.findWithCourseByTypeIn(any()))
                .thenReturn(List.of(published, unrelated));
        when(signRepository.findByMeaningIgnoreCase("saludo")).thenReturn(Optional.empty());
        when(mediaStorage.copy("lsa/hola.glb", "lsa/saludo.glb")).thenReturn(true);

        var detail = service.update(hola.getId(), new UpdateSignRequest(" saludo ", null, null));

        assertEquals("saludo", detail.meaning());
        assertEquals(
                "{\"sign\":\"saludo\",\"options\":[\"saludo\",\"chau\"]}", published.getConfig());
        assertEquals("{\"concepts\":[\"chau\"]}", unrelated.getConfig());
        verify(lessonBlockRepository).saveAll(List.of(published));
        verify(userLearnedSignRepository).renameSign("hola", "saludo");
        assertEquals("lsa/saludo.glb", hola.getAnimationUrl());
    }

    @Test
    void renameKeepsTheOldKeyWhenTheFileCannotBeCopied() {
        when(signRepository.findByMeaningIgnoreCase("saludo")).thenReturn(Optional.empty());
        when(mediaStorage.copy(anyString(), anyString())).thenReturn(false);

        service.update(hola.getId(), new UpdateSignRequest("saludo", null, null));

        assertEquals("lsa/hola.glb", hola.getAnimationUrl());
        assertEquals("saludo", hola.getMeaning());
    }

    @Test
    void renameRejectsTakenOrInvalidMeanings() {
        Sign other = Sign.builder().id(UUID.randomUUID()).meaning("Chau").build();
        when(signRepository.findByMeaningIgnoreCase("chau")).thenReturn(Optional.of(other));

        assertThrows(
                ResourceAlreadyInUseException.class,
                () -> service.update(hola.getId(), new UpdateSignRequest("chau", null, null)));
        assertThrows(
                InvalidInputException.class,
                () -> service.update(hola.getId(), new UpdateSignRequest("¿hola?", null, null)));
        verify(userLearnedSignRepository, never()).renameSign(anyString(), anyString());
    }

    @Test
    void caseOnlyRenameIsAllowed() {
        when(signRepository.findByMeaningIgnoreCase("Hola")).thenReturn(Optional.of(hola));

        service.update(hola.getId(), new UpdateSignRequest("Hola", null, null));

        assertEquals("Hola", hola.getMeaning());
        verify(userLearnedSignRepository).renameSign("hola", "Hola");
    }

    @Test
    void updatesHandednessAndYoutubeAndLeavesNullsAlone() {
        hola.setYoutubeUrl("https://youtu.be/old");

        service.update(hola.getId(), new UpdateSignRequest(null, Handedness.TWO_HANDED, null));
        assertEquals(Handedness.TWO_HANDED, hola.getHandedness());
        assertEquals("https://youtu.be/old", hola.getYoutubeUrl());
        assertEquals("hola", hola.getMeaning());

        service.update(hola.getId(), new UpdateSignRequest(null, null, "  "));
        assertNull(hola.getYoutubeUrl());

        assertThrows(
                InvalidInputException.class,
                () ->
                        service.update(
                                hola.getId(),
                                new UpdateSignRequest(null, null, "https://vimeo.com/1")));
    }

    @Test
    void uploadDetectsTheFormatFromTheBytes() {
        var detail =
                service.uploadAnimation(
                        hola.getId(),
                        new MockMultipartFile("file", "x.bin", null, MP4),
                        AdminSignService.View.FRONT);

        verify(mediaStorage).replace(eq("lsa/hola.mp4"), eq(MP4), eq("video/mp4"));
        assertEquals("lsa/hola.mp4", hola.getAnimationUrl());
        assertEquals("mp4", detail.animationFormat());
        assertEquals(hola.getAnimationUpdatedAt(), detail.animationUpdatedAt());

        service.uploadAnimation(
                hola.getId(),
                new MockMultipartFile("file", "a.glb", null, GLB),
                AdminSignService.View.FRONT);
        verify(mediaStorage).replace(eq("lsa/hola.glb"), eq(GLB), eq("model/gltf-binary"));
    }

    @Test
    void uploadRejectsOtherFiles() {
        assertThrows(
                InvalidInputException.class,
                () ->
                        service.uploadAnimation(
                                hola.getId(),
                                new MockMultipartFile("file", "a.mov", null, MOV),
                                AdminSignService.View.FRONT));
        assertThrows(
                InvalidInputException.class,
                () ->
                        service.uploadAnimation(
                                hola.getId(),
                                new MockMultipartFile("file", "a.glb", null, new byte[0]),
                                AdminSignService.View.FRONT));
        assertThrows(
                InvalidInputException.class,
                () ->
                        service.uploadAnimation(
                                hola.getId(),
                                new MockMultipartFile(
                                        "file",
                                        "a.png",
                                        null,
                                        "\u0089PNG\r\n\u001a\n0000".getBytes()),
                                AdminSignService.View.FRONT));
    }

    @Test
    void createUsesLsaAndRejectsDuplicates() {
        when(signLanguageRepository.findByCode("LSA")).thenReturn(Optional.of(lsa));
        when(signRepository.existsByMeaningIgnoreCase("buen día")).thenReturn(false);

        var detail =
                service.create(
                        new CreateAdminSignRequest("buen  día", Handedness.TWO_HANDED, null));

        assertEquals("buen día", detail.meaning());
        assertEquals("LSA", detail.signLanguageCode());
        assertNull(detail.mediaUrl());

        when(signRepository.existsByMeaningIgnoreCase("hola")).thenReturn(true);
        assertThrows(
                ResourceAlreadyInUseException.class,
                () ->
                        service.create(
                                new CreateAdminSignRequest("hola", Handedness.ONE_HANDED, null)));
    }

    @Test
    void deleteRefusesUsedOrReportedSigns() {
        when(lessonBlockRepository.findWithCourseByTypeIn(any()))
                .thenReturn(
                        List.of(
                                block(
                                        VersionStatus.ARCHIVED,
                                        BlockType.MATCH,
                                        "{\"concepts\":[\"hola\"]}")));
        assertThrows(ResourceAlreadyInUseException.class, () -> service.delete(hola.getId()));

        when(lessonBlockRepository.findWithCourseByTypeIn(any())).thenReturn(List.of());
        when(signReportRepository.existsBySignId(hola.getId())).thenReturn(true);
        assertThrows(ResourceAlreadyInUseException.class, () -> service.delete(hola.getId()));

        when(signReportRepository.existsBySignId(hola.getId())).thenReturn(false);
        service.delete(hola.getId());
        verify(signRepository).delete(hola);
    }

    @Test
    void lateralNeedsAnMp4FrontAndAnMp4File() {
        assertThrows(
                InvalidInputException.class,
                () ->
                        service.uploadAnimation(
                                hola.getId(),
                                new MockMultipartFile("file", "l.mp4", null, MP4),
                                AdminSignService.View.LATERAL));

        hola.setAnimationUrl("lsa/hola.mp4");
        assertThrows(
                InvalidInputException.class,
                () ->
                        service.uploadAnimation(
                                hola.getId(),
                                new MockMultipartFile("file", "l.glb", null, GLB),
                                AdminSignService.View.LATERAL));

        var detail =
                service.uploadAnimation(
                        hola.getId(),
                        new MockMultipartFile("file", "l.mp4", null, MP4),
                        AdminSignService.View.LATERAL);

        verify(mediaStorage).replace(eq("lsa/hola_lateral.mp4"), eq(MP4), eq("video/mp4"));
        assertEquals("lsa/hola_lateral.mp4", detail.lateralAnimationUrl());
        assertEquals("url:lsa/hola_lateral.mp4", detail.lateralMediaUrl());
        assertEquals("lsa/hola.mp4", hola.getAnimationUrl());
    }

    @Test
    void switchingToGlbDropsTheLateralVideo() {
        hola.setAnimationUrl("lsa/hola.mp4");
        hola.setLateralAnimationUrl("lsa/hola_lateral.mp4");

        service.uploadAnimation(
                hola.getId(),
                new MockMultipartFile("file", "a.glb", null, GLB),
                AdminSignService.View.FRONT);

        assertNull(hola.getLateralAnimationUrl());
        assertEquals("lsa/hola.glb", hola.getAnimationUrl());
    }

    @Test
    void renameMovesTheLateralVideoToo() {
        hola.setAnimationUrl("lsa/hola.mp4");
        hola.setLateralAnimationUrl("lsa/hola_lateral.mp4");
        when(signRepository.findByMeaningIgnoreCase("saludo")).thenReturn(Optional.empty());
        when(mediaStorage.copy(anyString(), anyString())).thenReturn(true);

        service.update(hola.getId(), new UpdateSignRequest("saludo", null, null));

        assertEquals("lsa/saludo.mp4", hola.getAnimationUrl());
        assertEquals("lsa/saludo_lateral.mp4", hola.getLateralAnimationUrl());
    }

    @Test
    void detailReportsAMissingFile() {
        when(mediaStorage.isAvailable("lsa/hola.glb")).thenReturn(false);

        assertEquals(Boolean.FALSE, service.detail(hola.getId()).mediaAvailable());

        service.removeLateral(hola.getId());
        assertNull(hola.getLateralAnimationUrl());
    }
}
