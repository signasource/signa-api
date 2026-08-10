package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.dto.CourseDetailResponse;
import com.signasource.signa_api.learning.dto.CourseSummaryResponse;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock private CourseRepository courseRepository;

    @Mock private CourseVersionRepository courseVersionRepository;

    @InjectMocks private CourseService courseService;

    private UUID signLanguageId;
    private UUID courseId;
    private SignLanguage signLanguage;
    private Course course;
    private CourseVersion activeVersion;
    private Topic topic;

    @BeforeEach
    void setUp() {
        signLanguageId = UUID.randomUUID();
        courseId = UUID.randomUUID();

        signLanguage =
                SignLanguage.builder()
                        .id(signLanguageId)
                        .code("LSA")
                        .name("Lengua de Señas Argentina")
                        .countryCode("ARG")
                        .build();

        course =
                Course.builder()
                        .id(courseId)
                        .name("LSA Básico")
                        .description("Curso introductorio")
                        .isFree(true)
                        .coverUrl("http://example.com/cover.jpg")
                        .signLanguage(signLanguage)
                        .build();

        activeVersion =
                CourseVersion.builder()
                        .id(UUID.randomUUID())
                        .version("v1.0")
                        .publishedAt(Instant.now())
                        .status(VersionStatus.PUBLISHED)
                        .course(course)
                        .build();

        topic =
                Topic.builder()
                        .id(UUID.randomUUID())
                        .code("MOD-01")
                        .name("Saludos")
                        .description("Aprende a saludar")
                        .order(1)
                        .courseVersion(activeVersion)
                        .build();
    }

    @Test
    void shouldReturnCoursesCatalog() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Course> coursePage = new PageImpl<>(List.of(course));

        when(courseRepository.findBySignLanguageId(eq(signLanguageId), any(Pageable.class)))
                .thenReturn(coursePage);

        Page<CourseSummaryResponse> response =
                courseService.getCoursesCatalog(signLanguageId, pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());

        CourseSummaryResponse summary = response.getContent().get(0);
        assertEquals(course.getId(), summary.id());
        assertEquals(course.getName(), summary.name());
        assertEquals(signLanguage.getCode(), summary.signLanguageCode());

        verify(courseRepository).findBySignLanguageId(signLanguageId, pageable);
    }

    @Test
    void shouldReturnCourseDetailWhenActiveVersionExists() {
        activeVersion.setCourse(course);
        activeVersion.setTopics(List.of(topic));

        when(courseVersionRepository.findByCourseIdAndStatus(courseId, VersionStatus.PUBLISHED))
                .thenReturn(Optional.of(activeVersion));

        CourseDetailResponse response = courseService.getCourseDetail(courseId);

        assertNotNull(response);
        assertEquals(course.getId(), response.id());
        assertEquals("v1.0", response.activeVersion());
        assertEquals(1, response.topics().size());
        assertEquals(topic.getName(), response.topics().get(0).name());

        verify(courseVersionRepository).findByCourseIdAndStatus(courseId, VersionStatus.PUBLISHED);
        verifyNoInteractions(courseRepository);
    }

    @Test
    void shouldThrowNotFoundWhenCourseHasNoPublishedVersion() {
        when(courseVersionRepository.findByCourseIdAndStatus(courseId, VersionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        NotFoundException exception =
                assertThrows(
                        NotFoundException.class, () -> courseService.getCourseDetail(courseId));

        assertTrue(exception.getMessage().contains("Active published version not found"));
        verify(courseVersionRepository).findByCourseIdAndStatus(courseId, VersionStatus.PUBLISHED);
        verifyNoInteractions(courseRepository);
    }
}
