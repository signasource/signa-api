package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.auth.service.EmailService;
import com.signasource.signa_api.exceptions.ForbiddenException;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.organizations.dto.AcceptInvitationResponse;
import com.signasource.signa_api.organizations.dto.InvitationPreviewResponse;
import com.signasource.signa_api.organizations.dto.SeatResponse;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.PurchaseSeatRepository;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class SeatInvitationService {

    private static final int TOKEN_BYTES = 32;

    private final PurchaseSeatRepository seatRepository;
    private final OrganizationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final OrganizationEnrollmentService enrollmentService;
    private final EmailService emailService;
    private final PurchasePolicy policy;
    private final SecureRandom random = new SecureRandom();

    public void invite(PurchaseSeat seat, Instant now) {
        seat.setInvitationToken(generateUniqueToken());
        seat.setStatus(SeatStatus.INVITED);
        seat.setInvitedAt(now);
        seat.setLastReminderAt(null);
        seat.setAutoReminderSentAt(null);
        seatRepository.save(seat);
        mail(seat, false);
    }

    public void remind(PurchaseSeat seat, Instant now) {
        if (seat.statusAt(now) != SeatStatus.INVITED) {
            throw new InvalidInputException("Only pending invitations can be reminded");
        }
        Instant next = nextManualReminderAt(seat);
        if (next != null && now.isBefore(next)) {
            throw new InvalidInputException("A reminder was sent recently; try again later");
        }
        seat.setLastReminderAt(now);
        seatRepository.save(seat);
        mail(seat, true);
    }

    @Transactional
    public int sendDueAutoReminders(Instant now) {
        List<PurchaseSeat> due =
                seatRepository.findDueForAutoReminder(now.minus(policy.autoReminderDelay()), now);
        for (PurchaseSeat seat : due) {
            seat.setAutoReminderSentAt(now);
            seatRepository.save(seat);
            mail(seat, true);
        }
        return due.size();
    }

    public Instant nextManualReminderAt(PurchaseSeat seat) {
        Instant last = latest(seat.getInvitedAt(), seat.getLastReminderAt());
        return last == null ? null : last.plus(policy.manualReminderCooldown());
    }

    public SeatResponse toResponse(PurchaseSeat seat, Instant now) {
        return SeatResponse.from(seat, now, nextManualReminderAt(seat));
    }

    @Transactional(readOnly = true)
    public InvitationPreviewResponse preview(String token) {
        PurchaseSeat seat = findByToken(token);
        CoursePurchase purchase = seat.getPurchase();
        return new InvitationPreviewResponse(
                purchase.getOrganization().getName(),
                purchase.getCourse().getName(),
                seat.getEmail(),
                seat.statusAt(Instant.now()),
                purchase.getInvitationDeadline(),
                purchase.getDurationDays(),
                userRepository.existsByEmail(seat.getEmail()));
    }

    @Transactional
    public AcceptInvitationResponse accept(User user, String token) {
        PurchaseSeat seat = findByToken(token);
        CoursePurchase purchase = seat.getPurchase();
        Organization organization = purchase.getOrganization();
        Course course = purchase.getCourse();
        Instant now = Instant.now();

        if (!seat.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new ForbiddenException("This invitation was sent to another email");
        }
        SeatStatus status = seat.statusAt(now);
        if (status == SeatStatus.ACTIVE
                && seat.getUser() != null
                && seat.getUser().getId().equals(user.getId())) {
            return response(seat);
        }
        if (status == SeatStatus.INVITATION_EXPIRED) {
            throw new InvalidInputException("This invitation expired");
        }
        if (status != SeatStatus.INVITED) {
            throw new InvalidInputException("This invitation is no longer valid");
        }
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new InvalidInputException("Organization is not active");
        }

        joinAsMember(user, organization);

        Instant accessExpiresAt = now.plus(Duration.ofDays(purchase.getDurationDays()));
        enrollmentService.grantCourse(user, organization, course, accessExpiresAt);

        seat.setStatus(SeatStatus.ACTIVE);
        seat.setActivatedAt(now);
        seat.setAccessExpiresAt(accessExpiresAt);
        seat.setUser(user);
        seatRepository.save(seat);

        boolean changed = false;
        if (!user.isVerified()) {
            user.setVerified(true);
            changed = true;
        }
        if (user.getCurrentCourse() == null) {
            user.setCurrentCourse(course);
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
        }
        return response(seat);
    }

    @Transactional
    public void revokeActiveSeats(User user, UUID organizationId) {
        Instant now = Instant.now();
        for (PurchaseSeat seat :
                seatRepository.findByUserIdAndPurchaseOrganizationIdAndStatus(
                        user.getId(), organizationId, SeatStatus.ACTIVE)) {
            seat.setStatus(SeatStatus.REVOKED);
            seat.setRevokedAt(now);
            seatRepository.save(seat);
        }
    }

    private void joinAsMember(User user, Organization organization) {
        OrganizationMember member = memberRepository.findByUserId(user.getId()).orElse(null);
        boolean active = member != null && member.getStatus() == MemberStatus.ACTIVE;
        boolean sameOrganization =
                member != null && member.getOrganization().getId().equals(organization.getId());
        if (active && sameOrganization) {
            return;
        }
        if (active) {
            throw new ResourceAlreadyInUseException("User already belongs to another organization");
        }
        if (member == null) {
            member = OrganizationMember.builder().user(user).build();
        }
        member.setOrganization(organization);
        member.setRole(MemberRole.MEMBER);
        member.setStatus(MemberStatus.ACTIVE);
        member.setRemovedAt(null);
        memberRepository.save(member);
    }

    private static AcceptInvitationResponse response(PurchaseSeat seat) {
        CoursePurchase purchase = seat.getPurchase();
        return new AcceptInvitationResponse(
                purchase.getOrganization().getName(),
                purchase.getCourse().getId(),
                purchase.getCourse().getName(),
                seat.getActivatedAt(),
                seat.getAccessExpiresAt());
    }

    private PurchaseSeat findByToken(String token) {
        return seatRepository
                .findByInvitationToken(token)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));
    }

    private void mail(PurchaseSeat seat, boolean reminder) {
        CoursePurchase purchase = seat.getPurchase();
        String to = seat.getEmail();
        String organizationName = purchase.getOrganization().getName();
        String courseName = purchase.getCourse().getName();
        String token = seat.getInvitationToken();
        Instant deadline = purchase.getInvitationDeadline();
        int days = purchase.getDurationDays();
        afterCommit(
                () ->
                        emailService.sendSeatInvitationEmail(
                                to, organizationName, courseName, token, deadline, days, reminder));
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }

    private String generateUniqueToken() {
        String token;
        do {
            byte[] bytes = new byte[TOKEN_BYTES];
            random.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (seatRepository.existsByInvitationToken(token));
        return token;
    }

    private static Instant latest(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
}
