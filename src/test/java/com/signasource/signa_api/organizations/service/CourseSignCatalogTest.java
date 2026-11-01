package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.organizations.dto.CatalogModuleResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseSignCatalogTest {

    @Mock private LessonBlockRepository lessonBlockRepository;

    private CourseSignCatalog catalog;
    private UUID versionId;
    private Lesson greetings;
    private Lesson emergencies;

    @BeforeEach
    void setUp() {
        catalog = new CourseSignCatalog(lessonBlockRepository, new ObjectMapper());
        versionId = UUID.randomUUID();
        greetings =
                Lesson.builder()
                        .id(UUID.randomUUID())
                        .topic(Topic.builder().id(UUID.randomUUID()).title("Saludos").build())
                        .build();
        emergencies =
                Lesson.builder()
                        .id(UUID.randomUUID())
                        .topic(Topic.builder().id(UUID.randomUUID()).title("Urgencias").build())
                        .build();
    }

    private static LessonBlock block(Lesson lesson, String config) {
        return LessonBlock.builder()
                .id(UUID.randomUUID())
                .type(BlockType.INTRODUCE_SIGN)
                .lesson(lesson)
                .config(config)
                .build();
    }

    @Test
    void shouldGroupTheSignsByModuleInOrder() {
        when(lessonBlockRepository.findIntroducedSigns(versionId))
                .thenReturn(
                        List.of(
                                block(greetings, "{\"meaning\":\"hola\",\"word\":\"Hola\"}"),
                                block(greetings, "{\"meaning\":\"gracias\",\"word\":\"Gracias\"}"),
                                block(emergencies, "{\"meaning\":\"dolor\",\"word\":\"Dolor\"}")));

        List<CatalogModuleResponse> modules = catalog.modules(versionId);

        assertEquals(
                List.of(
                        new CatalogModuleResponse("Saludos", List.of("Hola", "Gracias")),
                        new CatalogModuleResponse("Urgencias", List.of("Dolor"))),
                modules);
        assertEquals(3, CourseSignCatalog.signCount(modules));
    }

    @Test
    void shouldListARepeatedSignOnceInTheFirstModule() {
        when(lessonBlockRepository.findIntroducedSigns(versionId))
                .thenReturn(
                        List.of(
                                block(greetings, "{\"word\":\"Hola\"}"),
                                block(emergencies, "{\"word\":\"hola\"}")));

        List<CatalogModuleResponse> modules = catalog.modules(versionId);

        assertEquals(List.of(new CatalogModuleResponse("Saludos", List.of("Hola"))), modules);
    }

    @Test
    void shouldFallBackToTheMeaningAndSkipUnreadableBlocks() {
        when(lessonBlockRepository.findIntroducedSigns(versionId))
                .thenReturn(
                        List.of(
                                block(greetings, "{\"meaning\":\"buen-dia\",\"word\":\" \"}"),
                                block(greetings, "not json"),
                                block(greetings, null),
                                block(emergencies, "{\"word\":42}")));

        List<CatalogModuleResponse> modules = catalog.modules(versionId);

        assertEquals(List.of(new CatalogModuleResponse("Saludos", List.of("buen-dia"))), modules);
    }
}
