package com.signasource.signa_api.admin.service;

import com.signasource.signa_api.admin.dto.AdminCourseResponse;
import com.signasource.signa_api.admin.dto.AdminOrganizationResponse;
import com.signasource.signa_api.admin.dto.AdminSignReportResponse;
import com.signasource.signa_api.admin.dto.AdminStatsResponse;
import com.signasource.signa_api.admin.dto.AdminUserResponse;
import com.signasource.signa_api.admin.dto.AdminWaitlistEntryResponse;
import com.signasource.signa_api.admin.dto.CreateAdminRequest;
import com.signasource.signa_api.admin.dto.UpdateAdminRequest;
import com.signasource.signa_api.admin.dto.UpdateOrganizationStatusRequest;
import com.signasource.signa_api.admin.dto.UpdateSignReportRequest;
import com.signasource.signa_api.admin.dto.UpdateUserRequest;
import com.signasource.signa_api.auth.entity.TokenType;
import com.signasource.signa_api.auth.repository.TokenRepository;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.entity.ReportReason;
import com.signasource.signa_api.learning.entity.ReportStatus;
import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignReport;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.SignReportRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
import com.signasource.signa_api.notification.entity.NotificationCode;
import com.signasource.signa_api.notification.service.NotificationService;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.users.entity.AuthProvider;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserSettings;
import com.signasource.signa_api.users.repository.UserRepository;
import com.signasource.signa_api.users.repository.UserSettingsRepository;
import com.signasource.signa_api.users.service.UserService;
import com.signasource.signa_api.waitlist.repository.WaitlistRepository;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final SignReportRepository signReportRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final WaitlistRepository waitlistRepository;
    private final CourseRepository courseRepository;
    private final SignRepository signRepository;
    private final UserStatsRepository userStatsRepository;
    private final TokenRepository tokenRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    // ── Stats ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        long activeOrgs = organizationRepository.countByStatus(OrganizationStatus.ACTIVE);
        long pendingOrgs = organizationRepository.countByStatus(OrganizationStatus.PENDING);
        long openReports =
                signReportRepository.countByStatus(ReportStatus.PENDING)
                        + signReportRepository.countByStatus(ReportStatus.REVIEWED);
        long totalUsers = userRepository.countByEnabled(true);
        long waitlistCount = waitlistRepository.count();
        long totalSigns = signRepository.count();
        return new AdminStatsResponse(
                activeOrgs, pendingOrgs, openReports, totalUsers, 0, waitlistCount, totalSigns);
    }

    // ── Organizations ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AdminOrganizationResponse> getOrganizations(
            List<OrganizationStatus> statuses, Pageable pageable) {
        List<OrganizationStatus> filter =
                (statuses == null || statuses.isEmpty())
                        ? List.of(OrganizationStatus.values())
                        : statuses;
        Page<Organization> page = organizationRepository.findByStatusIn(filter, pageable);
        List<AdminOrganizationResponse> content =
                page.getContent().stream()
                        .map(
                                org -> {
                                    List<OrganizationMember> admins =
                                            memberRepository.findByOrganizationIdAndRoleAndStatus(
                                                    org.getId(),
                                                    MemberRole.ADMIN,
                                                    MemberStatus.ACTIVE);
                                    String adminName =
                                            admins.isEmpty()
                                                    ? null
                                                    : admins.get(0).getUser().getName()
                                                            + " "
                                                            + admins.get(0).getUser().getLastName();
                                    String adminEmail =
                                            admins.isEmpty()
                                                    ? null
                                                    : admins.get(0).getUser().getEmail();
                                    int activeMembers =
                                            memberRepository
                                                    .findByOrganizationIdAndRoleAndStatus(
                                                            org.getId(),
                                                            MemberRole.MEMBER,
                                                            MemberStatus.ACTIVE)
                                                    .size();
                                    return AdminOrganizationResponse.from(
                                            org, activeMembers, adminName, adminEmail);
                                })
                        .toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminOrganizationResponse getOrganization(UUID id) {
        Organization org = findOrg(id);
        List<OrganizationMember> admins =
                memberRepository.findByOrganizationIdAndRoleAndStatus(
                        id, MemberRole.ADMIN, MemberStatus.ACTIVE);
        String adminName =
                admins.isEmpty()
                        ? null
                        : admins.get(0).getUser().getName()
                                + " "
                                + admins.get(0).getUser().getLastName();
        String adminEmail = admins.isEmpty() ? null : admins.get(0).getUser().getEmail();
        int activeMembers =
                memberRepository
                        .findByOrganizationIdAndRoleAndStatus(
                                id, MemberRole.MEMBER, MemberStatus.ACTIVE)
                        .size();
        return AdminOrganizationResponse.from(org, activeMembers, adminName, adminEmail);
    }

    @Transactional
    public AdminOrganizationResponse updateOrganizationStatus(
            UUID id, UpdateOrganizationStatusRequest request) {
        Organization org = findOrg(id);
        OrganizationStatus newStatus = request.status();

        if (newStatus == OrganizationStatus.ACTIVE
                && org.getStatus() == OrganizationStatus.PENDING) {
            org.setStatus(OrganizationStatus.ACTIVE);
            organizationRepository.save(org);
        } else if (newStatus == OrganizationStatus.REJECTED
                || newStatus == OrganizationStatus.SUSPENDED) {
            org.setStatus(newStatus);
            organizationRepository.save(org);
        } else {
            throw new InvalidInputException(
                    "Invalid status transition from " + org.getStatus() + " to " + newStatus);
        }

        return getOrganization(id);
    }

    // ── Sign Reports ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminSignReportResponse> getSignReports(
            ReportStatus status,
            ReportReason reason,
            String signQuery,
            LocalDate from,
            LocalDate to) {
        LocalDateTime fromDateTime = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDateTime = to != null ? to.atTime(LocalTime.MAX) : null;
        String query = (signQuery != null && !signQuery.isBlank()) ? signQuery.trim() : null;

        return signReportRepository
                .findAll(signReportSpec(status, reason, query, fromDateTime, toDateTime))
                .stream()
                .map(AdminSignReportResponse::from)
                .toList();
    }

    /**
     * Builds the admin report query as a Specification so only the filters actually provided add a
     * predicate. A JPQL {@code :param IS NULL OR ...} query binds typed nulls that PostgreSQL
     * cannot infer (e.g. a null string parameter is read as {@code bytea}, breaking {@code
     * lower(...)}).
     */
    private Specification<SignReport> signReportSpec(
            ReportStatus status,
            ReportReason reason,
            String signQuery,
            LocalDateTime from,
            LocalDateTime to) {
        return (root, query, cb) -> {
            Fetch<SignReport, Sign> signFetch = root.fetch("sign", JoinType.INNER);
            signFetch.fetch("signLanguage", JoinType.INNER);
            root.fetch("user", JoinType.INNER);
            root.fetch("resolvedBy", JoinType.LEFT);
            query.orderBy(cb.desc(root.get("createdAt")));

            List<Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (reason != null) predicates.add(cb.equal(root.get("reason"), reason));
            if (signQuery != null) {
                Join<SignReport, Sign> sign = root.join("sign", JoinType.INNER);
                predicates.add(
                        cb.like(
                                cb.lower(sign.get("meaning")),
                                "%" + signQuery.toLowerCase(Locale.ROOT) + "%"));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDateTime>get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional
    public AdminSignReportResponse updateSignReport(
            UUID reportId, UpdateSignReportRequest request, User actor) {
        SignReport report =
                signReportRepository
                        .findById(reportId)
                        .orElseThrow(() -> new NotFoundException("Report not found: " + reportId));

        boolean wasResolved = report.getStatus() == ReportStatus.RESOLVED;
        report.setStatus(request.status());
        if (request.adminNote() != null) report.setAdminNote(request.adminNote());
        if (request.status() == ReportStatus.RESOLVED
                || request.status() == ReportStatus.REJECTED) {
            report.setResolvedBy(actor);
            report.setResolvedAt(LocalDateTime.now());
        }
        signReportRepository.save(report);

        if (!wasResolved && request.status() == ReportStatus.RESOLVED) {
            notificationService.notifyUser(
                    report.getUser().getId(),
                    NotificationCode.SIGN_REPORT_RESOLVED,
                    Map.of("sign", report.getSign().getMeaning()));
        }

        return AdminSignReportResponse.from(report);
    }

    // ── Users ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getUsers(String query, Role roleFilter, Pageable pageable) {
        String pattern = query != null ? "%" + query.toLowerCase() + "%" : null;
        Page<User> users = userRepository.adminSearch(query, pattern, roleFilter, pageable);
        return users.map(
                u -> {
                    String orgName =
                            memberRepository
                                    .findByUserId(u.getId())
                                    .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                                    .map(m -> m.getOrganization().getName())
                                    .orElse(null);
                    UserStats stats = userStatsRepository.findByUserId(u.getId()).orElse(null);
                    return AdminUserResponse.from(u, orgName, stats);
                });
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(UUID userId) {
        User user = findUser(userId);
        String orgName =
                memberRepository
                        .findByUserId(userId)
                        .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                        .map(m -> m.getOrganization().getName())
                        .orElse(null);
        UserStats stats = userStatsRepository.findByUserId(userId).orElse(null);
        return AdminUserResponse.from(user, orgName, stats);
    }

    @Transactional
    public AdminUserResponse updateUser(User actor, UUID userId, UpdateUserRequest request) {
        User user = findExistingUser(userId);
        boolean losesAccess =
                Boolean.FALSE.equals(request.enabled())
                        || (request.role() != null && request.role() != Role.ADMIN);
        if (actor.getId().equals(userId) && losesAccess) {
            throw new InvalidInputException("You cannot disable or demote your own account");
        }
        if (request.role() != null) user.setRole(request.role());
        if (request.enabled() != null) user.setEnabled(request.enabled());
        userRepository.save(user);
        if (Boolean.FALSE.equals(request.enabled())) {
            tokenRepository.deleteByUserAndType(user, TokenType.REFRESH);
        }
        return getUser(userId);
    }

    @Transactional
    public void deleteUser(User actor, UUID userId) {
        if (actor.getId().equals(userId)) {
            throw new InvalidInputException("You cannot delete your own account");
        }
        userService.deleteAccount(findExistingUser(userId));
    }

    private User findExistingUser(UUID userId) {
        User user = findUser(userId);
        if (user.getEmail().endsWith(UserService.DELETED_EMAIL_DOMAIN)) {
            throw new NotFoundException("User not found");
        }
        return user;
    }

    // ── Admins ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminUserResponse> getAdmins() {
        return userRepository.findByRole(Role.ADMIN).stream()
                .filter(User::isEnabled)
                .map(
                        u ->
                                AdminUserResponse.from(
                                        u,
                                        null,
                                        userStatsRepository.findByUserId(u.getId()).orElse(null)))
                .toList();
    }

    @Transactional
    public AdminUserResponse createAdmin(CreateAdminRequest request) {
        Optional<User> existing = userRepository.findFirstByEmailIgnoreCase(request.email().trim());
        if (existing.isPresent()) {
            return promoteToAdmin(existing.get(), request.password(), request.avatar());
        }
        String base = request.name().toLowerCase().replaceAll("[^a-z0-9]", "");
        if (base.length() > 20) base = base.substring(0, 20);
        if (base.isEmpty()) base = "admin";
        String username = base;
        int suffix = 1;
        while (userRepository.existsByUsername(username)) {
            username = base + suffix++;
        }

        User admin =
                User.builder()
                        .email(request.email())
                        .username(username)
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .name(request.name())
                        .lastName(request.lastName())
                        .role(Role.ADMIN)
                        .enabled(true)
                        .verified(true)
                        .providers(Set.of(AuthProvider.LOCAL))
                        .avatar(blankToNull(request.avatar()))
                        .build();
        userRepository.save(admin);
        userSettingsRepository.save(UserSettings.builder().user(admin).build());
        return AdminUserResponse.from(admin, null, null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    @Transactional
    public AdminUserResponse updateAdmin(UUID adminId, UpdateAdminRequest request) {
        User admin = findExistingUser(adminId);
        if (admin.getRole() != Role.ADMIN) {
            throw new NotFoundException("Admin not found");
        }
        if (request.name() != null) admin.setName(request.name().trim());
        if (request.lastName() != null) admin.setLastName(request.lastName().trim());
        if (request.email() != null) {
            String email = request.email().trim();
            if (!email.equalsIgnoreCase(admin.getEmail())) {
                if (userRepository
                        .findFirstByEmailIgnoreCase(email)
                        .filter(other -> !other.getId().equals(adminId))
                        .isPresent()) {
                    throw new ResourceAlreadyInUseException("Email already in use");
                }
                admin.setEmail(email);
            }
        }
        boolean passwordChanged = request.password() != null;
        if (passwordChanged) {
            admin.setPasswordHash(passwordEncoder.encode(request.password()));
            admin.getProviders().add(AuthProvider.LOCAL);
        }
        if (request.avatar() != null) {
            admin.setAvatar(request.avatar().isEmpty() ? null : request.avatar());
        }
        userRepository.save(admin);
        if (passwordChanged) {
            // A new password signs the admin out everywhere else.
            tokenRepository.deleteByUserAndType(admin, TokenType.REFRESH);
        }
        return getUser(adminId);
    }

    private AdminUserResponse promoteToAdmin(User user, String password, String avatar) {
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        user.setVerified(true);
        if (user.getPasswordHash() == null) {
            user.setPasswordHash(passwordEncoder.encode(password));
            user.getProviders().add(AuthProvider.LOCAL);
        }
        // A photo given while promoting is applied; otherwise the account keeps the one it has.
        if (blankToNull(avatar) != null) user.setAvatar(avatar);
        userRepository.save(user);
        return getUser(user.getId());
    }

    // ── Waitlist ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AdminWaitlistEntryResponse> getWaitlist(Pageable pageable) {
        return waitlistRepository
                .findAllByOrderByCreatedAtDesc(pageable)
                .map(AdminWaitlistEntryResponse::from);
    }

    // ── Content ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminCourseResponse> getCourses() {
        return courseRepository.findAll().stream()
                .map(course -> AdminCourseResponse.from(course, 0))
                .toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Organization findOrg(UUID id) {
        return organizationRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Organization not found"));
    }

    private User findUser(UUID id) {
        return userRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
