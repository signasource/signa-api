package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.learning.repository.projection.TopicLessonTotalView;
import com.signasource.signa_api.organizations.entity.OrganizationCourse;
import com.signasource.signa_api.organizations.repository.OrganizationCourseRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves what an organization's progress is measured against: the published version of every
 * course it contracted and how many lessons each one has.
 */
@Component
@RequiredArgsConstructor
public class OrganizationContentScope {

    private final OrganizationCourseRepository organizationCourseRepository;
    private final CourseVersionRepository courseVersionRepository;
    private final TopicRepository topicRepository;

    public ContentScope resolve(UUID organizationId) {
        List<UUID> courseIds =
                organizationCourseRepository
                        .findByOrganizationIdOrderByContractedAtAsc(organizationId)
                        .stream()
                        .map(OrganizationCourse::getCourse)
                        .map(course -> course.getId())
                        .toList();
        if (courseIds.isEmpty()) {
            return new ContentScope(List.of(), Map.of(), Map.of(), 0);
        }

        List<CourseVersion> versions =
                courseVersionRepository.findByCourseIdInAndStatus(
                        courseIds, VersionStatus.PUBLISHED);
        if (versions.isEmpty()) {
            return new ContentScope(List.of(), Map.of(), Map.of(), 0);
        }

        Map<UUID, Long> lessonsByVersion = new HashMap<>();
        Map<UUID, Long> lessonsByTopic = new HashMap<>();
        long total = 0;
        for (TopicLessonTotalView topic :
                topicRepository.findTopicLessonTotals(
                        versions.stream().map(CourseVersion::getId).toList())) {
            lessonsByVersion.merge(topic.getCourseVersionId(), topic.getTotalLessons(), Long::sum);
            lessonsByTopic.put(topic.getTopicId(), topic.getTotalLessons());
            total += topic.getTotalLessons();
        }
        return new ContentScope(versions, lessonsByVersion, lessonsByTopic, total);
    }

    public record ContentScope(
            List<CourseVersion> versions,
            Map<UUID, Long> lessonsByVersion,
            Map<UUID, Long> lessonsByTopic,
            long totalLessons) {

        public List<UUID> versionIds() {
            return versions.stream().map(CourseVersion::getId).toList();
        }

        public boolean isEmpty() {
            return versions.isEmpty();
        }
    }
}
