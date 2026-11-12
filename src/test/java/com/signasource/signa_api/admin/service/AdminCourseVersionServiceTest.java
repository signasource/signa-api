package com.signasource.signa_api.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.admin.dto.AdminCourseVersionDetailResponse;
import com.signasource.signa_api.admin.dto.AdminCourseVersionListItemResponse;
import com.signasource.signa_api.admin.dto.CreateCourseRequest;
import com.signasource.signa_api.admin.dto.SaveCourseVersionRequest;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.users.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminCourseVersionServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private CourseVersionRepository courseVersionRepository;
    @Mock private SignLanguageRepository signLanguageRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private AdminCourseVersionService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SignLanguage lsa;
    private Course course;
    private CourseVersion draftVersion;

    @BeforeEach
    void setUp() throws Exception {
        // inject real ObjectMapper via reflection since @InjectMocks won't use it
        var field = AdminCourseVersionService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(service, objectMapper);

        lsa = SignLanguage.builder().id(UUID.randomUUID()).code("LSA").name("LSA").build();

        course =
                Course.builder()
                        .id(UUID.randomUUID())
                        .code("test-course")
                        .name("Test Course")
                        .isFree(true)
                        .signLanguage(lsa)
                        .build();

        draftVersion =
                CourseVersion.builder()
                        .id(UUID.randomUUID())
                        .version("1")
                        .status(VersionStatus.DRAFT)
                        .course(course)
                        .topics(new ArrayList<>())
                        .build();
    }

    @Test
    void shouldListVersions() {
        when(courseVersionRepository.findAllByOrderByStatusAscUpdatedAtDesc())
                .thenReturn(List.of(draftVersion));
        when(userRepository.findByEmailIn(any(Collection.class))).thenReturn(List.of());

        List<AdminCourseVersionListItemResponse> result = service.listVersions();

        assertEquals(1, result.size());
        assertEquals("Test Course", result.get(0).courseName());
    }

    @Test
    void shouldGetVersion() {
        when(courseVersionRepository.findWithFullTreeById(draftVersion.getId()))
                .thenReturn(Optional.of(draftVersion));

        AdminCourseVersionDetailResponse result = service.getVersion(draftVersion.getId());

        assertEquals("Test Course", result.name());
        assertEquals("DRAFT", result.status());
    }

    @Test
    void shouldThrowNotFoundWhenVersionMissing() {
        UUID id = UUID.randomUUID();
        when(courseVersionRepository.findWithFullTreeById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getVersion(id));
    }

    @Test
    void shouldCreateCourse() {
        when(signLanguageRepository.findByCode("LSA")).thenReturn(Optional.of(lsa));
        when(courseRepository.save(any(Course.class))).thenAnswer(i -> i.getArgument(0));
        when(courseVersionRepository.save(any(CourseVersion.class)))
                .thenAnswer(i -> i.getArgument(0));

        AdminCourseVersionDetailResponse result =
                service.createCourse(new CreateCourseRequest("My Course"), "admin@signa.com");

        assertEquals("My Course", result.name());
        assertEquals("DRAFT", result.status());
        assertEquals("admin@signa.com", result.updatedBy());
        verify(courseRepository).save(any(Course.class));
        verify(courseVersionRepository).save(any(CourseVersion.class));
    }

    @Test
    void shouldThrowWhenLsaMissing() {
        when(signLanguageRepository.findByCode("LSA")).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service.createCourse(new CreateCourseRequest("X"), "admin@signa.com"));
    }

    @Test
    void shouldForkVersion() {
        when(courseVersionRepository.findWithFullTreeById(draftVersion.getId()))
                .thenReturn(Optional.of(draftVersion));
        when(courseVersionRepository.findByCourseId(course.getId()))
                .thenReturn(List.of(draftVersion));
        when(courseVersionRepository.save(any(CourseVersion.class)))
                .thenAnswer(i -> i.getArgument(0));

        AdminCourseVersionDetailResponse result =
                service.forkVersion(draftVersion.getId(), "admin@signa.com");

        assertEquals("2", result.version());
        assertEquals("1", result.basedOnVersion());
    }

    @Test
    void shouldSaveVersion() {
        when(courseVersionRepository.findWithFullTreeById(draftVersion.getId()))
                .thenReturn(Optional.of(draftVersion));
        when(courseRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(courseVersionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SaveCourseVersionRequest request =
                new SaveCourseVersionRequest("Updated Name", "desc", true, null, null, List.of());
        AdminCourseVersionDetailResponse result =
                service.saveVersion(draftVersion.getId(), request, "admin@signa.com");

        assertEquals("Updated Name", result.name());
        assertNotNull(result.updatedAt());
    }

    @Test
    void shouldThrowWhenSavingNonDraft() {
        CourseVersion published =
                CourseVersion.builder()
                        .id(UUID.randomUUID())
                        .version("1")
                        .status(VersionStatus.PUBLISHED)
                        .course(course)
                        .topics(new ArrayList<>())
                        .build();
        when(courseVersionRepository.findWithFullTreeById(published.getId()))
                .thenReturn(Optional.of(published));

        SaveCourseVersionRequest request =
                new SaveCourseVersionRequest("X", null, true, null, null, List.of());
        assertThrows(
                InvalidInputException.class,
                () -> service.saveVersion(published.getId(), request, "admin@signa.com"));
    }

    @Test
    void shouldPublishVersion() {
        when(courseVersionRepository.findWithFullTreeById(draftVersion.getId()))
                .thenReturn(Optional.of(draftVersion));
        when(courseVersionRepository.findByCourseId(course.getId()))
                .thenReturn(List.of(draftVersion));
        when(courseVersionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AdminCourseVersionDetailResponse result = service.publishVersion(draftVersion.getId());

        assertEquals("PUBLISHED", result.status());
        assertNotNull(result.publishedAt());
    }

    @Test
    void shouldArchiveExistingPublishedOnPublish() {
        CourseVersion published =
                CourseVersion.builder()
                        .id(UUID.randomUUID())
                        .version("0.1.0")
                        .status(VersionStatus.PUBLISHED)
                        .course(course)
                        .topics(new ArrayList<>())
                        .build();
        when(courseVersionRepository.findWithFullTreeById(draftVersion.getId()))
                .thenReturn(Optional.of(draftVersion));
        when(courseVersionRepository.findByCourseId(course.getId()))
                .thenReturn(List.of(draftVersion, published));
        when(courseVersionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.publishVersion(draftVersion.getId());

        assertEquals(VersionStatus.ARCHIVED, published.getStatus());
    }

    @Test
    void shouldThrowWhenPublishingNonDraft() {
        CourseVersion published =
                CourseVersion.builder()
                        .id(UUID.randomUUID())
                        .version("1")
                        .status(VersionStatus.PUBLISHED)
                        .course(course)
                        .topics(new ArrayList<>())
                        .build();
        when(courseVersionRepository.findWithFullTreeById(published.getId()))
                .thenReturn(Optional.of(published));

        assertThrows(InvalidInputException.class, () -> service.publishVersion(published.getId()));
    }

    @Test
    void shouldSaveVersionWithTopicsAndBlocks() {
        when(courseVersionRepository.findWithFullTreeById(draftVersion.getId()))
                .thenReturn(Optional.of(draftVersion));
        when(courseRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(courseVersionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SaveCourseVersionRequest.BlockData block =
                new SaveCourseVersionRequest.BlockData(
                        null, "INFO", null, java.util.Map.of("title", "Hello", "text", "World"));
        SaveCourseVersionRequest.LessonData lesson =
                new SaveCourseVersionRequest.LessonData(null, "Lesson 1", null, List.of(block));
        SaveCourseVersionRequest.TopicData topic =
                new SaveCourseVersionRequest.TopicData(
                        null, "Unit 1", "Subtitle", null, List.of(lesson));
        SaveCourseVersionRequest request =
                new SaveCourseVersionRequest("Course", "desc", true, null, null, List.of(topic));

        AdminCourseVersionDetailResponse result =
                service.saveVersion(draftVersion.getId(), request, "admin@signa.com");

        assertEquals(1, result.topics().size());
        assertEquals(1, result.topics().get(0).lessons().size());
        assertEquals(1, result.topics().get(0).lessons().get(0).blocks().size());
        assertEquals("INFO", result.topics().get(0).lessons().get(0).blocks().get(0).type());
    }
}
