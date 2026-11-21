package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.auth.service.EmailService;
import com.signasource.signa_api.common.AfterCommit;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.organizations.dto.ParticipantReminderResponse;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An admin's nudge to a participant from the panel: an email with the courses they can still take.
 * Same cooldown as invitation reminders, so nobody gets mailed twice in a row.
 */
@Service
@RequiredArgsConstructor
public class ParticipantReminderService {

    private final OrganizationAccessService accessService;
    private final OrganizationMemberRepository memberRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final EmailService emailService;
    private final PurchasePolicy policy;

    @Transactional
    public ParticipantReminderResponse remind(User actor, UUID organizationId, UUID userId) {
        accessService.requireManage(actor, organizationId);
        Instant now = Instant.now();
        OrganizationMember member =
                memberRepository
                        .findByOrganizationIdAndUserId(organizationId, userId)
                        .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                        .filter(m -> m.getRole() == MemberRole.MEMBER)
                        .orElseThrow(() -> new NotFoundException("Participant not found"));

        Instant last = member.getLastReminderAt();
        if (last != null && now.isBefore(last.plus(policy.manualReminderCooldown()))) {
            throw new InvalidInputException("A reminder was sent recently; try again later");
        }
        List<UserCourseEnrollment> running =
                enrollmentRepository.findByUserIdAndOrganizationId(userId, organizationId).stream()
                        .filter(e -> e.grantsAccess(now))
                        .toList();
        if (running.isEmpty()) {
            throw new InvalidInputException("The participant has no course to take");
        }

        member.setLastReminderAt(now);
        memberRepository.save(member);

        User user = member.getUser();
        String to = user.getEmail();
        String name = user.getName();
        String organizationName = member.getOrganization().getName();
        List<String> courses =
                running.stream()
                        .map(e -> e.getCourseVersion().getCourse().getName())
                        .distinct()
                        .sorted()
                        .toList();
        Instant expiresAt =
                running.stream()
                        .map(UserCourseEnrollment::getAccessExpiresAt)
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder())
                        .orElse(null);
        AfterCommit.run(
                () ->
                        emailService.sendParticipantReminderEmail(
                                to, name, organizationName, courses, expiresAt));
        return new ParticipantReminderResponse(now, now.plus(policy.manualReminderCooldown()));
    }
}
