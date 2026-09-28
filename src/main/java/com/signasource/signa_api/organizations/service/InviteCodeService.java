package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.dto.CourseSummaryResponse;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.learning.service.CourseTrackingService;
import com.signasource.signa_api.organizations.dto.CreateInviteCodeRequest;
import com.signasource.signa_api.organizations.dto.InviteCodeResponse;
import com.signasource.signa_api.organizations.dto.RedeemInviteCodeResponse;
import com.signasource.signa_api.organizations.entity.InviteCode;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.repository.InviteCodeRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.users.entity.User;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InviteCodeService {

    /** Excludes visually ambiguous characters (0/O, 1/I) so a printed code is easy to type. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int CODE_LENGTH = 8;

    private final InviteCodeRepository inviteCodeRepository;
    private final OrganizationRepository organizationRepository;
    private final CourseRepository courseRepository;
    private final CourseVersionRepository courseVersionRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final CourseTrackingService courseTrackingService;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public InviteCodeResponse createInviteCode(UUID organizationId, CreateInviteCodeRequest request) {
        Organization organization =
                organizationRepository
                        .findById(organizationId)
                        .orElseThrow(() -> new NotFoundException("Organization not found"));

        Course course =
                courseRepository
                        .findById(request.courseId())
                        .orElseThrow(() -> new NotFoundException("Course not found"));

        InviteCode inviteCode =
                InviteCode.builder()
                        .code(generateUniqueCode())
                        .organization(organization)
                        .course(course)
                        .expiresAt(request.expiresAt())
                        .maxUses(request.maxUses())
                        .build();

        return InviteCodeResponse.from(inviteCodeRepository.save(inviteCode));
    }

    @Transactional(readOnly = true)
    public List<InviteCodeResponse> getOrganizationInviteCodes(UUID organizationId) {
        return inviteCodeRepository.findByOrganizationId(organizationId).stream()
                .map(InviteCodeResponse::from)
                .toList();
    }

    @Transactional
    public RedeemInviteCodeResponse redeem(User user, String rawCode) {
        InviteCode inviteCode =
                inviteCodeRepository
                        .findByCode(normalize(rawCode))
                        .orElseThrow(
                                () ->
                                        new InvalidInputException(
                                                "Ese código no existe o ya venció. Revisalo con tu organización."));

        if (!inviteCode.isActive()
                || (inviteCode.getExpiresAt() != null
                        && inviteCode.getExpiresAt().isBefore(Instant.now()))
                || (inviteCode.getMaxUses() != null
                        && inviteCode.getUseCount() >= inviteCode.getMaxUses())) {
            throw new InvalidInputException(
                    "Ese código no existe o ya venció. Revisalo con tu organización.");
        }

        Course course = inviteCode.getCourse();
        CourseVersion courseVersion =
                courseVersionRepository
                        .findByCourseIdAndStatus(course.getId(), VersionStatus.PUBLISHED)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "Active published version not found for course ID: "
                                                        + course.getId()));

        boolean alreadyEnrolled =
                enrollmentRepository.existsByUserIdAndCourseVersionId(
                        user.getId(), courseVersion.getId());

        if (!alreadyEnrolled) {
            courseTrackingService.enrollUserInCourse(
                    user, courseVersion.getId(), inviteCode.getOrganization(), inviteCode.getExpiresAt());
            inviteCode.setUseCount(inviteCode.getUseCount() + 1);
            inviteCodeRepository.save(inviteCode);
        }

        return new RedeemInviteCodeResponse(
                inviteCode.getOrganization().getName(),
                CourseSummaryResponse.from(course),
                alreadyEnrolled,
                inviteCode.getExpiresAt());
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = randomCode();
        } while (inviteCodeRepository.existsByCode(code));
        return code;
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String normalize(String rawCode) {
        return rawCode.trim().toUpperCase().replace("-", "");
    }
}
