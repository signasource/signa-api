package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.auth.service.EmailService;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.organizations.dto.InviteByEmailRequest;
import com.signasource.signa_api.organizations.dto.InviteCodeResponse;
import com.signasource.signa_api.organizations.dto.RedeemInviteCodeResponse;
import com.signasource.signa_api.organizations.entity.InviteCode;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.repository.InviteCodeRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
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

    private static final String INVALID_CODE_MESSAGE =
            "Ese código no existe o ya venció. Revisalo con tu organización.";

    /** Excludes visually ambiguous characters (0/O, 1/I) so a printed code is easy to type. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int CODE_LENGTH = 8;

    private final InviteCodeRepository inviteCodeRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final OrganizationAccessService accessService;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    /**
     * Invites someone to administer the organization from the web panel. Same single-use,
     * email-bound code as a participant invite, but redeeming it grants the ADMIN role and mails a
     * link to the panel instead of the app.
     */
    @Transactional
    public InviteCodeResponse inviteAdminByEmail(
            User actor, UUID organizationId, InviteByEmailRequest request) {
        accessService.requireManage(actor, organizationId);
        InviteCode inviteCode =
                saveCode(
                        findOrganization(organizationId),
                        request.expiresAt(),
                        1,
                        request.email().trim().toLowerCase(),
                        MemberRole.ADMIN);
        emailService.sendOrganizationAdminInviteEmail(
                inviteCode.getEmail(),
                inviteCode.getOrganization().getName(),
                inviteCode.getCode());
        return InviteCodeResponse.from(inviteCode);
    }

    @Transactional(readOnly = true)
    public List<InviteCodeResponse> getOrganizationInviteCodes(User actor, UUID organizationId) {
        accessService.requireManage(actor, organizationId);
        return inviteCodeRepository.findByOrganizationId(organizationId).stream()
                .map(InviteCodeResponse::from)
                .toList();
    }

    @Transactional
    public void deactivateInviteCode(User actor, UUID organizationId, UUID inviteCodeId) {
        accessService.requireManage(actor, organizationId);
        InviteCode inviteCode =
                inviteCodeRepository
                        .findByIdAndOrganizationId(inviteCodeId, organizationId)
                        .orElseThrow(() -> new NotFoundException("Invite code not found"));
        inviteCode.setActive(false);
        inviteCodeRepository.save(inviteCode);
    }

    @Transactional
    public RedeemInviteCodeResponse redeem(User user, String rawCode) {
        InviteCode inviteCode =
                inviteCodeRepository
                        .findByCode(normalize(rawCode))
                        .orElseThrow(() -> new InvalidInputException(INVALID_CODE_MESSAGE));

        if (inviteCode.getMemberRole() != MemberRole.ADMIN || !isRedeemable(inviteCode, user)) {
            throw new InvalidInputException(INVALID_CODE_MESSAGE);
        }

        Organization organization = inviteCode.getOrganization();
        OrganizationMember member = memberRepository.findByUserId(user.getId()).orElse(null);

        boolean activeMember = member != null && member.getStatus() == MemberStatus.ACTIVE;
        boolean inThisOrganization =
                activeMember && member.getOrganization().getId().equals(organization.getId());
        if (activeMember && !inThisOrganization) {
            throw new ResourceAlreadyInUseException("User already belongs to another organization");
        }

        boolean alreadyAdmin = inThisOrganization && member.getRole() == MemberRole.ADMIN;
        if (!alreadyAdmin) {
            if (member == null) {
                member = OrganizationMember.builder().user(user).build();
            }
            member.setOrganization(organization);
            member.setRole(MemberRole.ADMIN);
            member.setStatus(MemberStatus.ACTIVE);
            member.setRemovedAt(null);
            memberRepository.save(member);

            inviteCode.setUseCount(inviteCode.getUseCount() + 1);
            inviteCodeRepository.save(inviteCode);
        }

        if (user.getRole() == Role.USER) {
            user.setRole(Role.ORG_ADMIN);
            userRepository.save(user);
        }
        return new RedeemInviteCodeResponse(
                organization.getName(),
                List.of(),
                alreadyAdmin,
                inviteCode.getExpiresAt(),
                MemberRole.ADMIN);
    }

    private boolean isRedeemable(InviteCode inviteCode, User user) {
        boolean expired =
                inviteCode.getExpiresAt() != null
                        && inviteCode.getExpiresAt().isBefore(Instant.now());
        boolean exhausted =
                inviteCode.getMaxUses() != null
                        && inviteCode.getUseCount() >= inviteCode.getMaxUses();
        boolean wrongRecipient =
                inviteCode.getEmail() != null
                        && !inviteCode.getEmail().equalsIgnoreCase(user.getEmail());
        return inviteCode.isActive() && !expired && !exhausted && !wrongRecipient;
    }

    private Organization findOrganization(UUID organizationId) {
        return organizationRepository
                .findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found"));
    }

    private InviteCode saveCode(
            Organization organization,
            Instant expiresAt,
            Integer maxUses,
            String email,
            MemberRole memberRole) {
        return inviteCodeRepository.save(
                InviteCode.builder()
                        .code(generateUniqueCode())
                        .organization(organization)
                        .expiresAt(expiresAt)
                        .maxUses(maxUses)
                        .email(email)
                        .memberRole(memberRole)
                        .build());
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
