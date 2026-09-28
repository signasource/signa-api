package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationCourse;
import com.signasource.signa_api.organizations.repository.OrganizationCourseRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
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

    private final OrganizationCourseRepository organizationCourseRepository;
    private final OrganizationMemberRepository memberRepository;
    private final CourseVersionRepository courseVersionRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

    @Transactional
    public List<Course> grantContractedCourses(
            User user, Organization organization, Instant accessExpiresAt) {
        List<Course> courses =
                organizationCourseRepository
                        .findByOrganizationIdOrderByContractedAtAsc(organization.getId())
                        .stream()
                        .map(OrganizationCourse::getCourse)
                        .toList();
        courses.forEach(course -> grantCourse(user, organization, course, accessExpiresAt));
        return courses;
    }

    @Transactional
    public void grantCourseToActiveMembers(Organization organization, Course course) {
        memberRepository
                .findByOrganizationIdAndRoleAndStatus(
                        organization.getId(), MemberRole.MEMBER, MemberStatus.ACTIVE)
                .forEach(member -> grantCourse(member.getUser(), organization, course, null));
    }

    @Transactional
    public void revokeCourseFromMembers(UUID organizationId, UUID courseId) {
        enrollmentRepository
                .findByOrganizationIdAndCourseVersionCourseId(organizationId, courseId)
                .forEach(this::drop);
    }

    @Transactional
    public void revokeAllFromUser(User user, UUID organizationId) {
        List<UserCourseEnrollment> enrollments =
                enrollmentRepository.findByUserIdAndOrganizationId(user.getId(), organizationId);
        enrollments.forEach(this::drop);

        Course current = user.getCurrentCourse();
        boolean currentWasRevoked =
                current != null
                        && enrollments.stream()
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

    private void grantCourse(
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
        enrollment.setAccessExpiresAt(accessExpiresAt);
        enrollmentRepository.save(enrollment);
    }

    private void drop(UserCourseEnrollment enrollment) {
        enrollment.setStatus(EnrollmentStatus.DROPPED);
        enrollmentRepository.save(enrollment);
    }
}
