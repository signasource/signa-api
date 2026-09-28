package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.learning.dto.CourseSummaryResponse;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.organizations.dto.CreateOrganizationRequest;
import com.signasource.signa_api.organizations.dto.MyOrganizationResponse;
import com.signasource.signa_api.organizations.dto.OrganizationCourseResponse;
import com.signasource.signa_api.organizations.dto.OrganizationResponse;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationCourse;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.repository.OrganizationCourseRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationCourseRepository organizationCourseRepository;
    private final OrganizationMemberRepository memberRepository;
    private final CourseRepository courseRepository;
    private final CourseVersionRepository courseVersionRepository;
    private final UserRepository userRepository;
    private final OrganizationAccessService accessService;
    private final OrganizationEnrollmentService enrollmentService;

    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        if (organizationRepository.existsByNameIgnoreCase(request.name())) {
            throw new ResourceAlreadyInUseException(
                    "An organization with this name already exists");
        }

        Organization organization = Organization.builder().name(request.name()).build();
        return OrganizationResponse.from(organizationRepository.save(organization));
    }

    @Transactional
    public void addAdmin(UUID organizationId, String email) {
        Organization organization = findOrganization(organizationId);
        User target =
                userRepository
                        .findByEmail(email)
                        .filter(User::isEnabled)
                        .orElseThrow(() -> new NotFoundException("User not found"));

        OrganizationMember member = memberRepository.findByUserId(target.getId()).orElse(null);
        if (member != null
                && member.getStatus() == MemberStatus.ACTIVE
                && !member.getOrganization().getId().equals(organizationId)) {
            throw new ResourceAlreadyInUseException("User already belongs to another organization");
        }
        if (member == null) {
            member = OrganizationMember.builder().user(target).build();
        }
        member.setOrganization(organization);
        member.setRole(MemberRole.ADMIN);
        member.setStatus(MemberStatus.ACTIVE);
        member.setRemovedAt(null);
        memberRepository.save(member);

        if (target.getRole() == Role.USER) {
            target.setRole(Role.ORG_ADMIN);
            userRepository.save(target);
        }
    }

    @Transactional
    public OrganizationCourseResponse addCourse(UUID organizationId, UUID courseId) {
        Organization organization = findOrganization(organizationId);
        Course course =
                courseRepository
                        .findById(courseId)
                        .orElseThrow(() -> new NotFoundException("Course not found"));

        if (organizationCourseRepository.existsByOrganizationIdAndCourseId(
                organizationId, courseId)) {
            throw new ResourceAlreadyInUseException(
                    "Course already contracted by this organization");
        }
        if (courseVersionRepository
                .findByCourseIdAndStatus(courseId, VersionStatus.PUBLISHED)
                .isEmpty()) {
            throw new NotFoundException(
                    "Active published version not found for course ID: " + courseId);
        }

        OrganizationCourse saved =
                organizationCourseRepository.save(
                        OrganizationCourse.builder()
                                .organization(organization)
                                .course(course)
                                .build());
        enrollmentService.grantCourseToActiveMembers(organization, course);
        return OrganizationCourseResponse.from(saved);
    }

    @Transactional
    public void removeCourse(UUID organizationId, UUID courseId) {
        OrganizationCourse contracted =
                organizationCourseRepository
                        .findByOrganizationIdAndCourseId(organizationId, courseId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "Course is not contracted by this organization"));
        organizationCourseRepository.delete(contracted);
        enrollmentService.revokeCourseFromMembers(organizationId, courseId);
    }

    @Transactional(readOnly = true)
    public List<OrganizationCourseResponse> getCourses(User actor, UUID organizationId) {
        accessService.requireManage(actor, organizationId);
        return organizationCourseRepository
                .findByOrganizationIdOrderByContractedAtAsc(organizationId)
                .stream()
                .map(OrganizationCourseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MyOrganizationResponse getMyOrganization(User user) {
        OrganizationMember member =
                memberRepository
                        .findByUserId(user.getId())
                        .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "User does not belong to an organization"));
        Organization organization = member.getOrganization();

        List<CourseSummaryResponse> courses =
                organizationCourseRepository
                        .findByOrganizationIdOrderByContractedAtAsc(organization.getId())
                        .stream()
                        .map(oc -> CourseSummaryResponse.from(oc.getCourse()))
                        .toList();
        return new MyOrganizationResponse(
                organization.getId(),
                organization.getName(),
                member.getRole(),
                member.getJoinedAt(),
                courses);
    }

    private Organization findOrganization(UUID organizationId) {
        return organizationRepository
                .findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found"));
    }
}
