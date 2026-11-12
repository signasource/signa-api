package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.dto.CourseDetailResponse;
import com.signasource.signa_api.learning.dto.CourseDetailResponse.CourseContentStats;
import com.signasource.signa_api.learning.dto.CourseSummaryResponse;
import com.signasource.signa_api.learning.dto.LessonPreviewResponse;
import com.signasource.signa_api.learning.dto.TopicSummaryResponse;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.learning.util.BlockSignExtractor;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
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

    /** Block types that are exercises: the learner answers something (INFO and intros are not). */
    private static final Set<BlockType> EXERCISE_TYPES =
            EnumSet.of(
                    BlockType.SELECT_MEANING,
                    BlockType.SELECT_SIGN,
                    BlockType.CONTEXT_RESPONSE,
                    BlockType.MATCH,
                    BlockType.VISUAL_RECOGNITION,
                    BlockType.PERFORM_SIGN,
                    BlockType.SPELL_NAME);

    /** Block types that recognise the learner's signing through the camera. */
    private static final Set<BlockType> CAMERA_TYPES =
            EnumSet.of(BlockType.PERFORM_SIGN, BlockType.SPELL_NAME);

    private final CourseRepository courseRepository;
    private final CourseVersionRepository courseVersionRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final BlockSignExtractor blockSignExtractor;

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

        Instant now = Instant.now();
        CourseVersion activeVersion = resolveVersionForUser(user, courseId, now);
        Course course = activeVersion.getCourse();

        // Course-wide aggregates, folded while the syllabus is built (a single pass over blocks).
        Set<String> courseSigns = new LinkedHashSet<>();
        Set<BlockType> exerciseTypes = EnumSet.noneOf(BlockType.class);
        boolean[] camera = {false};
        int lessonCount = 0;

        List<TopicSummaryResponse> topicsResponse = new ArrayList<>();
        for (Topic topic : activeVersion.getTopics()) {
            List<LessonPreviewResponse> lessons = new ArrayList<>();
            for (Lesson lesson : topic.getLessons()) {
                lessons.add(previewOf(lesson, courseSigns, exerciseTypes, camera));
                lessonCount++;
            }
            topicsResponse.add(TopicSummaryResponse.from(topic, List.copyOf(lessons)));
        }

        CourseContentStats stats =
                new CourseContentStats(
                        lessonCount,
                        courseSigns.size(),
                        camera[0],
                        List.copyOf(exerciseTypes),
                        courseSigns.stream().findFirst().orElse(null));

        boolean owned = ownedCourseIds(user).contains(course.getId());

        return CourseDetailResponse.from(course, activeVersion, owned, stats, topicsResponse);
    }

    /**
     * Returns the course version to show in the detail view. Enrolled users see the version they
     * are subscribed to (which may be archived if admins published a newer version after they
     * enrolled). Non-enrolled users see the currently published version.
     *
     * <p>When a user has active enrollments in multiple versions of the same course — e.g. they
     * were in v1 and later re-enrolled in v2 — the published version takes precedence.
     */
    private CourseVersion resolveVersionForUser(User user, UUID courseId, Instant now) {
        List<UserCourseEnrollment> active =
                enrollmentRepository
                        .findByUserIdAndCourseVersionCourseId(user.getId(), courseId)
                        .stream()
                        .filter(e -> e.grantsAccess(now))
                        .toList();

        if (!active.isEmpty()) {
            UUID versionId =
                    active.stream()
                            .filter(
                                    e ->
                                            e.getCourseVersion().getStatus()
                                                    == VersionStatus.PUBLISHED)
                            .map(e -> e.getCourseVersion().getId())
                            .findFirst()
                            .orElse(active.get(0).getCourseVersion().getId());
            return courseVersionRepository
                    .findWithFullTreeById(versionId)
                    .orElseThrow(() -> new NotFoundException("Course version not found"));
        }

        return courseVersionRepository
                .findByCourseIdAndStatus(courseId, VersionStatus.PUBLISHED)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        "Active published version not found for course ID: "
                                                + courseId));
    }

    /** Syllabus entry for a lesson; also folds its blocks into the course-wide aggregates. */
    private LessonPreviewResponse previewOf(
            Lesson lesson,
            Set<String> courseSigns,
            Set<BlockType> exerciseTypes,
            boolean[] camera) {
        Set<String> lessonSigns = new LinkedHashSet<>();
        for (LessonBlock block : lesson.getLessonBlocks()) {
            lessonSigns.addAll(blockSignExtractor.extract(block));
            if (EXERCISE_TYPES.contains(block.getType())) exerciseTypes.add(block.getType());
            if (CAMERA_TYPES.contains(block.getType())) camera[0] = true;
        }
        courseSigns.addAll(lessonSigns);
        return new LessonPreviewResponse(lesson.getName(), List.copyOf(lessonSigns));
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
