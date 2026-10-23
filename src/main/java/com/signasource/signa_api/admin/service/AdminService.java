package com.signasource.signa_api.admin.service;

import com.signasource.signa_api.admin.dto.AdminCourseResponse;
import com.signasource.signa_api.admin.dto.AdminOrganizationResponse;
import com.signasource.signa_api.admin.dto.AdminSignReportGroupResponse;
import com.signasource.signa_api.admin.dto.AdminSignResponse;
import com.signasource.signa_api.admin.dto.AdminStatsResponse;
import com.signasource.signa_api.admin.dto.AdminUserResponse;
import com.signasource.signa_api.admin.dto.AdminWaitlistEntryResponse;
import com.signasource.signa_api.admin.dto.CreateAdminRequest;
import com.signasource.signa_api.admin.dto.UpdateOrganizationStatusRequest;
import com.signasource.signa_api.admin.dto.UpdateSignReportGroupRequest;
import com.signasource.signa_api.admin.dto.UpdateUserRequest;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.ReportReason;
import com.signasource.signa_api.learning.entity.ReportStatus;
import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignReport;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.SignReportRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationCourse;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import com.signasource.signa_api.organizations.repository.OrganizationCourseRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.organizations.service.OrganizationEnrollmentService;
import com.signasource.signa_api.users.entity.AuthProvider;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserSettings;
import com.signasource.signa_api.users.repository.UserRepository;
import com.signasource.signa_api.users.repository.UserSettingsRepository;
import com.signasource.signa_api.waitlist.repository.WaitlistRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationCourseRepository organizationCourseRepository;
    private final OrganizationEnrollmentService enrollmentService;
    private final SignReportRepository signReportRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final WaitlistRepository waitlistRepository;
    private final CourseRepository courseRepository;
    private final SignRepository signRepository;
    private final UserStatsRepository userStatsRepository;
    private final PasswordEncoder passwordEncoder;

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
            if (request.courseIds() != null) {
                for (UUID courseId : request.courseIds()) {
                    if (!organizationCourseRepository.existsByOrganizationIdAndCourseId(
                            id, courseId)) {
                        Course course =
                                courseRepository
                                        .findById(courseId)
                                        .orElseThrow(
                                                () ->
                                                        new NotFoundException(
                                                                "Course not found: " + courseId));
                        organizationCourseRepository.save(
                                OrganizationCourse.builder()
                                        .organization(org)
                                        .course(course)
                                        .build());
                        enrollmentService.grantCourseToActiveMembers(org, course);
                    }
                }
            }
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
    public List<AdminSignReportGroupResponse> getSignReportGroups(ReportStatus statusFilter) {
        List<ReportStatus> statuses =
                statusFilter != null
                        ? List.of(statusFilter)
                        : List.of(
                                ReportStatus.PENDING,
                                ReportStatus.REVIEWED,
                                ReportStatus.RESOLVED,
                                ReportStatus.REJECTED);

        List<SignReport> reports = signReportRepository.findByStatusIn(statuses);

        Map<UUID, List<SignReport>> bySign =
                reports.stream().collect(Collectors.groupingBy(r -> r.getSign().getId()));

        return bySign.entrySet().stream()
                .map(e -> buildGroup(e.getValue()))
                .sorted(Comparator.comparingInt(g -> -g.reportCount()))
                .toList();
    }

    private AdminSignReportGroupResponse buildGroup(List<SignReport> reports) {
        Sign sign = reports.get(0).getSign();
        ReportReason topReason =
                reports.stream()
                        .collect(
                                Collectors.groupingBy(SignReport::getReason, Collectors.counting()))
                        .entrySet()
                        .stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(ReportReason.OTHER);

        ReportStatus groupStatus = deriveGroupStatus(reports);
        LocalDateTime lastAt =
                reports.stream()
                        .map(SignReport::getCreatedAt)
                        .max(Comparator.naturalOrder())
                        .orElse(null);

        List<AdminSignReportGroupResponse.ReportItem> items =
                reports.stream()
                        .sorted(Comparator.comparing(SignReport::getCreatedAt).reversed())
                        .map(
                                r ->
                                        new AdminSignReportGroupResponse.ReportItem(
                                                r.getId(),
                                                r.getUser().getUsername(),
                                                r.getReason(),
                                                r.getDescription(),
                                                r.getStatus(),
                                                r.getCreatedAt()))
                        .toList();

        return new AdminSignReportGroupResponse(
                sign.getId(),
                sign.getMeaning(),
                sign.getSignLanguage().getName(),
                sign.getHandedness().name(),
                reports.size(),
                topReason,
                groupStatus,
                lastAt,
                items);
    }

    private ReportStatus deriveGroupStatus(List<SignReport> reports) {
        Set<ReportStatus> statuses =
                reports.stream().map(SignReport::getStatus).collect(Collectors.toSet());
        if (statuses.contains(ReportStatus.PENDING)) return ReportStatus.PENDING;
        if (statuses.contains(ReportStatus.REVIEWED)) return ReportStatus.REVIEWED;
        if (statuses.contains(ReportStatus.RESOLVED)) return ReportStatus.RESOLVED;
        return ReportStatus.REJECTED;
    }

    @Transactional
    public AdminSignReportGroupResponse updateSignReportGroup(
            UUID signId, UpdateSignReportGroupRequest request, User actor) {
        List<SignReport> reports = signReportRepository.findBySignId(signId);
        if (reports.isEmpty()) throw new NotFoundException("No reports found for sign " + signId);

        for (SignReport report : reports) {
            report.setStatus(request.status());
            if (request.adminNote() != null) report.setAdminNote(request.adminNote());
            if (request.status() == ReportStatus.RESOLVED
                    || request.status() == ReportStatus.REJECTED) {
                report.setResolvedBy(actor);
                report.setResolvedAt(LocalDateTime.now());
            }
        }
        signReportRepository.saveAll(reports);
        return buildGroup(reports);
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
    public AdminUserResponse updateUser(UUID userId, UpdateUserRequest request) {
        User user = findUser(userId);
        if (request.role() != null) user.setRole(request.role());
        if (request.enabled() != null) user.setEnabled(request.enabled());
        userRepository.save(user);
        return getUser(userId);
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
        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceAlreadyInUseException("Email already in use");
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
                        .build();
        userRepository.save(admin);
        userSettingsRepository.save(UserSettings.builder().user(admin).build());
        return AdminUserResponse.from(admin, null, null);
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

    @Transactional(readOnly = true)
    public Page<AdminSignResponse> getSigns(String query, Pageable pageable) {
        List<ReportStatus> openStatuses = List.of(ReportStatus.PENDING, ReportStatus.REVIEWED);
        org.springframework.data.domain.Page<Sign> page;
        if (query != null && !query.isBlank()) {
            page = signRepository.findByMeaningContainingIgnoreCase(query, pageable);
        } else {
            page = signRepository.findAll(pageable);
        }
        List<AdminSignResponse> content =
                page.getContent().stream()
                        .map(
                                s -> {
                                    long openReports =
                                            signReportRepository.countBySignIdAndStatusIn(
                                                    s.getId(), openStatuses);
                                    return AdminSignResponse.from(s, openReports, 0);
                                })
                        .toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
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
                .filter(User::isEnabled)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
