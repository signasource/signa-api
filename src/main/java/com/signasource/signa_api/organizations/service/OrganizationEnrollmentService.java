package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps members' enrollments in sync with what their organization contracted. Access is revoked by
 * marking the enrollment {@code DROPPED} rather than deleting it, so the progress survives a later
 * re-join.
 */
@Service
@RequiredArgsConstructor
public class OrganizationEnrollmentService {

    private final CourseVersionRepository courseVersionRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

    @Transactional
    public void revokeAllFromUser(User user, UUID organizationId) {
        List<UserCourseEnrollment> enrollments =
                enrollmentRepository.findByUserIdAndOrganizationId(user.getId(), organizationId);
        enrollments.forEach(this::drop);

        Course current = user.getCurrentCourse();
        boolean currentWasRevoked =
                current != null
                        && enrollments.stream()
                                .filter(e -> e.getStatus() == EnrollmentStatus.DROPPED)
                                .anyMatch(
                                        e ->
                                                e.getCourseVersion()
                                                        .getCourse()
                                                        .getId()
                                                        .equals(current.getId()));
        if (currentWasRevoked) {
            user.setCurrentCourse(null);
            userRepository.save(user);
        }
    }

    @Transactional
    public void grantCourse(
            User user, Organization organization, Course course, Instant accessExpiresAt) {
        CourseVersion version =
                courseVersionRepository
                        .findByCourseIdAndStatus(course.getId(), VersionStatus.PUBLISHED)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "Active published version not found for course ID: "
                                                        + course.getId()));

        UserCourseEnrollment enrollment =
                enrollmentRepository
                        .findByUserIdAndCourseVersionId(user.getId(), version.getId())
                        .orElseGet(
                                () ->
                                        UserCourseEnrollment.builder()
                                                .user(user)
                                                .courseVersion(version)
                                                .status(EnrollmentStatus.ENROLLED)
                                                .build());

        if (enrollment.getStatus() == EnrollmentStatus.DROPPED) {
            enrollment.setStatus(EnrollmentStatus.ENROLLED);
        }
        enrollment.setOrganization(organization);
        // A free course is open to everyone, so the organization can't put an end date on it.
        enrollment.setAccessExpiresAt(course.isFree() ? null : accessExpiresAt);
        enrollmentRepository.save(enrollment);
    }

    /**
     * Ends a member's access to a paid course now, the way an expiration does: the enrollment stays
     * (so the progress is kept and readable) but no longer grants access. Free courses are left
     * alone, since anyone can take them.
     */
    @Transactional
    public void endCourseAccess(User user, UUID organizationId, UUID courseId, Instant now) {
        for (UserCourseEnrollment enrollment :
                enrollmentRepository.findByUserIdAndOrganizationId(user.getId(), organizationId)) {
            Course course = enrollment.getCourseVersion().getCourse();
            if (!course.getId().equals(courseId) || course.isFree()) {
                continue;
            }
            if (enrollment.getAccessExpiresAt() == null
                    || enrollment.getAccessExpiresAt().isAfter(now)) {
                enrollment.setAccessExpiresAt(now);
                enrollmentRepository.save(enrollment);
            }
        }
    }

    /**
     * Free courses are only detached from the organization: the learner could have joined them on
     * their own, and dropping one would lock them out of a course that is open to everyone.
     */
    private void drop(UserCourseEnrollment enrollment) {
        if (enrollment.getCourseVersion().getCourse().isFree()) {
            enrollment.setOrganization(null);
            enrollment.setAccessExpiresAt(null);
        } else {
            enrollment.setStatus(EnrollmentStatus.DROPPED);
        }
        enrollmentRepository.save(enrollment);
    }
}
