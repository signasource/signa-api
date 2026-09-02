package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.dto.CourseRoadmapResponse;
import com.signasource.signa_api.learning.dto.LessonRoadmapState;
import com.signasource.signa_api.learning.dto.RoadmapLessonResponse;
import com.signasource.signa_api.learning.dto.RoadmapTopicResponse;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.ProgressStatus;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.learning.repository.UserLessonProgressRepository;
import com.signasource.signa_api.learning.repository.projection.LessonBlockAggregateView;
import com.signasource.signa_api.learning.repository.projection.LessonProgressStatusView;
import com.signasource.signa_api.users.entity.User;
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
class CourseRoadmapServiceTest {

    @Mock private CourseVersionRepository courseVersionRepository;
    @Mock private TopicRepository topicRepository;
    @Mock private LessonBlockRepository lessonBlockRepository;
    @Mock private UserLessonProgressRepository lessonProgressRepository;

    @InjectMocks private CourseRoadmapService courseRoadmapService;

    private UUID courseId;
    private UUID versionId;
    private UUID userId;
    private User user;
    private Course course;
    private CourseVersion version;

    @BeforeEach
    void setUp() {
        courseId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        userId = UUID.randomUUID();

        user = mock(User.class);

        course = Course.builder().id(courseId).name("LSA Básico").build();
        version =
                CourseVersion.builder()
                        .id(versionId)
                        .version("v1.0")
                        .status(VersionStatus.PUBLISHED)
                        .course(course)
                        .build();
    }

    @Test
    void shouldThrowNotFoundWhenNoPublishedVersion() {
        when(courseVersionRepository.findByCourseIdAndStatus(courseId, VersionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        NotFoundException exception =
                assertThrows(
                        NotFoundException.class,
                        () -> courseRoadmapService.getCourseRoadmap(user, courseId));

        assertTrue(exception.getMessage().contains("Active published version not found"));
        verifyNoInteractions(topicRepository, lessonBlockRepository, lessonProgressRepository);
    }

    @Test
    void shouldBuildRoadmapResolvingEveryLessonState() {
        Lesson l1 = lesson("l1", "Intro", 1);
        Lesson l2 = lesson("l2", "Saludos", 2);
        Lesson l3 = lesson("l3", "Números", 3);
        Lesson l4 = lesson("l4", "Colores", 4);
        Lesson l5 = lesson("l5", "Familia", 5);
        Topic topic =
                topic(
                        "t1",
                        "Unidad 1",
                        "Básico",
                        "Conceptos básicos",
                        1,
                        List.of(l1, l2, l3, l4, l5));

        stubVersionAndTopics(List.of(topic));
        when(lessonBlockRepository.aggregateByCourseVersionId(versionId))
                .thenReturn(List.of(agg(l1.getId(), 4, 50)));
        when(lessonProgressRepository.findLessonStatuses(userId, versionId))
                .thenReturn(
                        List.of(
                                status(l1.getId(), ProgressStatus.COMPLETED),
                                status(l4.getId(), ProgressStatus.IN_PROGRESS),
                                status(l5.getId(), ProgressStatus.COMPLETED)));
        when(user.getId()).thenReturn(userId);

        CourseRoadmapResponse response = courseRoadmapService.getCourseRoadmap(user, courseId);

        assertEquals(courseId, response.courseId());
        assertEquals("LSA Básico", response.courseName());
        assertEquals("v1.0", response.activeVersion());
        assertEquals(1, response.topics().size());

        RoadmapTopicResponse topicResponse = response.topics().get(0);
        assertEquals("Unidad 1", topicResponse.title());
        assertEquals("Básico", topicResponse.subtitle());
        List<RoadmapLessonResponse> lessons = topicResponse.lessons();
        assertEquals(5, lessons.size());

        assertEquals(LessonRoadmapState.COMPLETED, lessons.get(0).state());
        assertEquals(4, lessons.get(0).blockCount());
        assertEquals(50, lessons.get(0).xpTotal());
        assertEquals(LessonRoadmapState.AVAILABLE, lessons.get(1).state());
        assertEquals(0, lessons.get(1).blockCount());
        assertEquals(0, lessons.get(1).xpTotal());
        assertEquals(LessonRoadmapState.LOCKED, lessons.get(2).state());
        assertEquals(LessonRoadmapState.IN_PROGRESS, lessons.get(3).state());
        assertEquals(LessonRoadmapState.COMPLETED, lessons.get(4).state());
    }

    @Test
    void shouldCarryUnlockFrontierAcrossTopics() {
        Lesson a = lesson("a", "A", 1);
        Lesson b = lesson("b", "B", 1);
        Lesson c = lesson("c", "C", 1);
        Topic topicA = topic("tA", "Unidad 1", "Topic A", "first", 1, List.of(a));
        Topic topicB = topic("tB", "Unidad 2", "Topic B", "second", 2, List.of(b));
        Topic topicC = topic("tC", "Unidad 3", "Topic C", "third", 3, List.of(c));

        stubVersionAndTopics(List.of(topicA, topicB, topicC));
        when(lessonBlockRepository.aggregateByCourseVersionId(versionId)).thenReturn(List.of());
        when(lessonProgressRepository.findLessonStatuses(userId, versionId))
                .thenReturn(List.of(status(a.getId(), ProgressStatus.COMPLETED)));
        when(user.getId()).thenReturn(userId);

        CourseRoadmapResponse response = courseRoadmapService.getCourseRoadmap(user, courseId);

        assertEquals(
                LessonRoadmapState.COMPLETED, response.topics().get(0).lessons().get(0).state());
        assertEquals(
                LessonRoadmapState.AVAILABLE, response.topics().get(1).lessons().get(0).state());
        assertEquals(LessonRoadmapState.LOCKED, response.topics().get(2).lessons().get(0).state());
    }

    private void stubVersionAndTopics(List<Topic> topics) {
        when(courseVersionRepository.findByCourseIdAndStatus(courseId, VersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        when(topicRepository.findRoadmapTopics(versionId)).thenReturn(topics);
    }

    private static Lesson lesson(String code, String name, int order) {
        return Lesson.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(name)
                .description(name + " description")
                .order(order)
                .build();
    }

    private static Topic topic(
            String code,
            String title,
            String subtitle,
            String description,
            int order,
            List<Lesson> lessons) {
        Topic topic =
                Topic.builder()
                        .id(UUID.randomUUID())
                        .code(code)
                        .title(title)
                        .subtitle(subtitle)
                        .description(description)
                        .order(order)
                        .build();
        topic.setLessons(lessons);
        return topic;
    }

    private static LessonBlockAggregateView agg(UUID lessonId, long blockCount, long xpTotal) {
        return new LessonBlockAggregateView() {
            @Override
            public UUID getLessonId() {
                return lessonId;
            }

            @Override
            public long getBlockCount() {
                return blockCount;
            }

            @Override
            public long getXpTotal() {
                return xpTotal;
            }
        };
    }

    private static LessonProgressStatusView status(UUID lessonId, ProgressStatus status) {
        return new LessonProgressStatusView() {
            @Override
            public UUID getLessonId() {
                return lessonId;
            }

            @Override
            public ProgressStatus getStatus() {
                return status;
            }
        };
    }
}
