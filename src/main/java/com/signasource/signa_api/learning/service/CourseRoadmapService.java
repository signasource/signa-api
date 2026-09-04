package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.dto.CourseRoadmapResponse;
import com.signasource.signa_api.learning.dto.LessonRoadmapState;
import com.signasource.signa_api.learning.dto.RoadmapLessonResponse;
import com.signasource.signa_api.learning.dto.RoadmapTopicResponse;
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
import com.signasource.signa_api.learning.util.BlockSignExtractor;
import com.signasource.signa_api.users.entity.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseRoadmapService {

    private final CourseVersionRepository courseVersionRepository;
    private final TopicRepository topicRepository;
    private final LessonBlockRepository lessonBlockRepository;
    private final UserLessonProgressRepository lessonProgressRepository;
    private final BlockSignExtractor blockSignExtractor;

    @Transactional(readOnly = true)
    public CourseRoadmapResponse getCourseRoadmap(User user, UUID courseId) {
        CourseVersion version =
                courseVersionRepository
                        .findByCourseIdAndStatus(courseId, VersionStatus.PUBLISHED)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "Active published version not found for course ID: "
                                                        + courseId));

        List<Topic> topics = topicRepository.findRoadmapTopics(version.getId());

        Map<UUID, long[]> blockAggByLesson =
                lessonBlockRepository.aggregateByCourseVersionId(version.getId()).stream()
                        .collect(
                                Collectors.toMap(
                                        LessonBlockAggregateView::getLessonId,
                                        v -> new long[] {v.getBlockCount(), v.getXpTotal()}));

        Map<UUID, List<String>> signsLearnedByLesson =
                lessonBlockRepository.findByCourseVersionId(version.getId()).stream()
                        .collect(
                                Collectors.groupingBy(
                                        b -> b.getLesson().getId(),
                                        Collectors.collectingAndThen(
                                                Collectors.toList(),
                                                blocks ->
                                                        blocks.stream()
                                                                .flatMap(
                                                                        b ->
                                                                                blockSignExtractor
                                                                                        .extract(b)
                                                                                        .stream())
                                                                .distinct()
                                                                .toList())));

        Map<UUID, ProgressStatus> statusByLesson =
                lessonProgressRepository.findLessonStatuses(user.getId(), version.getId()).stream()
                        .collect(
                                Collectors.toMap(
                                        LessonProgressStatusView::getLessonId,
                                        LessonProgressStatusView::getStatus));

        boolean previousCompleted = true;
        List<RoadmapTopicResponse> topicResponses = new ArrayList<>(topics.size());
        for (Topic topic : topics) {
            List<Lesson> lessons = topic.getLessons();
            List<RoadmapLessonResponse> lessonResponses = new ArrayList<>(lessons.size());
            for (Lesson lesson : lessons) {
                long[] agg = blockAggByLesson.getOrDefault(lesson.getId(), EMPTY_AGG);
                List<String> signsLearned = signsLearnedByLesson.getOrDefault(lesson.getId(), List.of());
                ProgressStatus status = statusByLesson.get(lesson.getId());
                lessonResponses.add(
                        RoadmapLessonResponse.of(
                                lesson,
                                (int) agg[0],
                                (int) agg[1],
                                signsLearned,
                                resolveState(status, previousCompleted)));
                previousCompleted = status == ProgressStatus.COMPLETED;
            }
            topicResponses.add(RoadmapTopicResponse.of(topic, lessonResponses));
        }

        return CourseRoadmapResponse.of(version.getCourse(), version, topicResponses);
    }

    private static final long[] EMPTY_AGG = {0L, 0L};

    private static LessonRoadmapState resolveState(
            ProgressStatus status, boolean previousCompleted) {
        if (status == ProgressStatus.COMPLETED) {
            return LessonRoadmapState.COMPLETED;
        }
        if (status == ProgressStatus.IN_PROGRESS) {
            return LessonRoadmapState.IN_PROGRESS;
        }
        return previousCompleted ? LessonRoadmapState.AVAILABLE : LessonRoadmapState.LOCKED;
    }
}
