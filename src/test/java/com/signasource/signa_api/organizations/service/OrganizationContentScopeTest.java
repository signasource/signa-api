package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.learning.repository.projection.TopicLessonTotalView;
import com.signasource.signa_api.organizations.entity.OrganizationCourse;
import com.signasource.signa_api.organizations.repository.OrganizationCourseRepository;
import com.signasource.signa_api.organizations.service.OrganizationContentScope.ContentScope;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationContentScopeTest {

    @Mock private OrganizationCourseRepository organizationCourseRepository;
    @Mock private CourseVersionRepository courseVersionRepository;
    @Mock private TopicRepository topicRepository;

    @InjectMocks private OrganizationContentScope contentScope;

    private final UUID organizationId = UUID.randomUUID();

    private TopicLessonTotalView total(UUID versionId, UUID topicId, long lessons) {
        TopicLessonTotalView view = mock(TopicLessonTotalView.class);
        when(view.getCourseVersionId()).thenReturn(versionId);
        when(view.getTopicId()).thenReturn(topicId);
        when(view.getTotalLessons()).thenReturn(lessons);
        return view;
    }

    @Test
    void shouldBeEmptyWhenNoCourseIsContracted() {
        when(organizationCourseRepository.findByOrganizationIdOrderByContractedAtAsc(
                        organizationId))
                .thenReturn(List.of());

        ContentScope scope = contentScope.resolve(organizationId);

        assertTrue(scope.isEmpty());
        assertEquals(0, scope.totalLessons());
        verifyNoInteractions(courseVersionRepository, topicRepository);
    }

    @Test
    void shouldBeEmptyWhenNoContractedCourseHasAPublishedVersion() {
        Course course = Course.builder().id(UUID.randomUUID()).build();
        when(organizationCourseRepository.findByOrganizationIdOrderByContractedAtAsc(
                        organizationId))
                .thenReturn(List.of(OrganizationCourse.builder().course(course).build()));
        when(courseVersionRepository.findByCourseIdInAndStatus(
                        List.of(course.getId()), VersionStatus.PUBLISHED))
                .thenReturn(List.of());

        assertTrue(contentScope.resolve(organizationId).isEmpty());
        verifyNoInteractions(topicRepository);
    }

    @Test
    void shouldSumLessonsPerVersionAndTopic() {
        Course course = Course.builder().id(UUID.randomUUID()).build();
        CourseVersion version =
                CourseVersion.builder().id(UUID.randomUUID()).course(course).build();
        UUID greetings = UUID.randomUUID();
        UUID numbers = UUID.randomUUID();
        when(organizationCourseRepository.findByOrganizationIdOrderByContractedAtAsc(
                        organizationId))
                .thenReturn(List.of(OrganizationCourse.builder().course(course).build()));
        when(courseVersionRepository.findByCourseIdInAndStatus(
                        List.of(course.getId()), VersionStatus.PUBLISHED))
                .thenReturn(List.of(version));
        TopicLessonTotalView greetingsTotal = total(version.getId(), greetings, 4);
        TopicLessonTotalView numbersTotal = total(version.getId(), numbers, 6);
        when(topicRepository.findTopicLessonTotals(List.of(version.getId())))
                .thenReturn(List.of(greetingsTotal, numbersTotal));

        ContentScope scope = contentScope.resolve(organizationId);

        assertFalse(scope.isEmpty());
        assertEquals(List.of(version.getId()), scope.versionIds());
        assertEquals(10, scope.totalLessons());
        assertEquals(10L, scope.lessonsByVersion().get(version.getId()));
        assertEquals(4L, scope.lessonsByTopic().get(greetings));
        assertEquals(6L, scope.lessonsByTopic().get(numbers));
    }
}
