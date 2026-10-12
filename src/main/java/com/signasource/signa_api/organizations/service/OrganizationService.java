package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.dto.CourseSummaryResponse;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.organizations.dto.CreateOrganizationRequest;
import com.signasource.signa_api.organizations.dto.MyOrganizationResponse;
import com.signasource.signa_api.organizations.dto.OrganizationCourseResponse;
import com.signasource.signa_api.organizations.dto.OrganizationPricingResponse;
import com.signasource.signa_api.organizations.dto.OrganizationResponse;
import com.signasource.signa_api.organizations.dto.RegisterOrganizationRequest;
import com.signasource.signa_api.organizations.dto.RegisterOrganizationResponse;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationCourse;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import com.signasource.signa_api.organizations.repository.OrganizationCourseRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.users.entity.AuthProvider;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserSettings;
import com.signasource.signa_api.users.repository.UserRepository;
import com.signasource.signa_api.users.repository.UserSettingsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final UserSettingsRepository userSettingsRepository;
    private final UserStatsRepository userStatsRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationAccessService accessService;
    private final OrganizationEnrollmentService enrollmentService;

    @Value("${organizations.price-per-person}")
    private BigDecimal pricePerPerson;

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

    @Transactional
    public RegisterOrganizationResponse registerOrganization(RegisterOrganizationRequest request) {
        if (organizationRepository.existsByNameIgnoreCase(request.orgName())) {
            throw new ResourceAlreadyInUseException(
                    "An organization with this name already exists");
        }
        if (organizationRepository.existsByCuit(request.cuit())) {
            throw new ResourceAlreadyInUseException(
                    "An organization with this CUIT already exists");
        }
        if (userRepository.existsByEmail(request.contactEmail())) {
            throw new ResourceAlreadyInUseException("Email already in use");
        }

        String base =
                request.orgName()
                        .toLowerCase()
                        .replaceAll("[^a-z0-9]", "_")
                        .replaceAll("_+", "_")
                        .replaceAll("(^_+|_+$)", "");
        if (base.length() > 20) base = base.substring(0, 20);
        if (base.isEmpty()) base = "org";
        String username = base;
        if (userRepository.existsByUsername(username)) {
            String prefix = base.substring(0, Math.min(base.length(), 15));
            username =
                    prefix
                            + "_"
                            + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        }

        User adminUser =
                User.builder()
                        .email(request.contactEmail())
                        .username(username)
                        .passwordHash(passwordEncoder.encode(request.adminPassword()))
                        .name(request.adminName())
                        .lastName("")
                        .role(Role.ORG_ADMIN)
                        .enabled(true)
                        .verified(true)
                        .providers(new HashSet<>(Set.of(AuthProvider.LOCAL)))
                        .build();
        userRepository.save(adminUser);

        userSettingsRepository.save(UserSettings.builder().user(adminUser).build());
        userStatsRepository.save(
                UserStats.builder().user(adminUser).gems(100).updatedAt(Instant.now()).build());

        Organization organization =
                Organization.builder()
                        .name(request.orgName())
                        .cuit(request.cuit())
                        .contactEmail(request.contactEmail())
                        .contactPhone(request.contactPhone())
                        .memberLimit(request.memberLimit())
                        .status(OrganizationStatus.PENDING)
                        .build();
        organizationRepository.save(organization);

        memberRepository.save(
                OrganizationMember.builder()
                        .user(adminUser)
                        .organization(organization)
                        .role(MemberRole.ADMIN)
                        .status(MemberStatus.ACTIVE)
                        .build());

        return new RegisterOrganizationResponse(
                organization.getId(), organization.getName(), organization.getStatus().name());
    }

    public OrganizationPricingResponse getPricing() {
        return new OrganizationPricingResponse(pricePerPerson, "ARS");
    }

    private Organization findOrganization(UUID organizationId) {
        return organizationRepository
                .findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found"));
    }
}
