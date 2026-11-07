package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.dto.CourseDetailResponse;
import com.signasource.signa_api.learning.dto.CourseSummaryResponse;
import com.signasource.signa_api.learning.dto.TopicSummaryResponse;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseVersionRepository courseVersionRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;

    @Transactional(readOnly = true)
    public Page<CourseSummaryResponse> getCoursesCatalog(
            User user, UUID signLanguageId, Pageable pageable) {
        Page<Course> courses = courseRepository.findBySignLanguageId(signLanguageId, pageable);
        Set<UUID> ownedCourseIds = ownedCourseIds(user);
        return courses.map(
                course ->
                        CourseSummaryResponse.from(
                                course, ownedCourseIds.contains(course.getId())));
    }

    @Transactional(readOnly = true)
    public CourseDetailResponse getCourseDetail(User user, UUID courseId) {

        CourseVersion activeVersion =
                courseVersionRepository
                        .findByCourseIdAndStatus(courseId, VersionStatus.PUBLISHED)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "Active published version not found for course ID: "
                                                        + courseId));

        Course course = activeVersion.getCourse();
        List<Topic> topics = activeVersion.getTopics();

        List<TopicSummaryResponse> topicsResponse =
                topics.stream().map(TopicSummaryResponse::from).toList();

        boolean owned = ownedCourseIds(user).contains(course.getId());

        return CourseDetailResponse.from(course, activeVersion, owned, topicsResponse);
    }

    /**
     * Ids of the courses the user can currently access (free self-enroll, paid purchase or org).
     */
    private Set<UUID> ownedCourseIds(User user) {
        Instant now = Instant.now();
        return enrollmentRepository.findWithCourseByUserId(user.getId()).stream()
                .filter(enrollment -> enrollment.grantsAccess(now))
                .map(enrollment -> enrollment.getCourseVersion().getCourse().getId())
                .collect(Collectors.toSet());
    }
}
