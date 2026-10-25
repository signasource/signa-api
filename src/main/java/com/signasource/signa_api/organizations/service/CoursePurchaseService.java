package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.organizations.dto.CatalogCourseResponse;
import com.signasource.signa_api.organizations.dto.CatalogModuleResponse;
import com.signasource.signa_api.organizations.dto.CreatePurchaseRequest;
import com.signasource.signa_api.organizations.dto.PurchaseCatalogResponse;
import com.signasource.signa_api.organizations.dto.PurchaseDetailResponse;
import com.signasource.signa_api.organizations.dto.PurchaseQuoteResponse;
import com.signasource.signa_api.organizations.dto.PurchaseResponse;
import com.signasource.signa_api.organizations.dto.UpdateSeatEmailRequest;
import com.signasource.signa_api.organizations.dto.VolumeDiscountResponse;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.organizations.repository.PurchaseSeatRepository;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CoursePurchaseService {

    private static final List<SeatStatus> STORED_LIVE_STATUSES =
            List.of(SeatStatus.PENDING_PAYMENT, SeatStatus.INVITED, SeatStatus.ACTIVE);

    private final CoursePurchaseRepository purchaseRepository;
    private final PurchaseSeatRepository seatRepository;
    private final OrganizationRepository organizationRepository;
    private final CourseRepository courseRepository;
    private final CourseVersionRepository courseVersionRepository;
    private final CourseSignCatalog signCatalog;
    private final OrganizationAccessService accessService;
    private final SeatInvitationService invitationService;
    private final PurchasePolicy policy;
    private final PurchasePricing pricing;

    @Transactional(readOnly = true)
    public PurchaseCatalogResponse catalog() {
        List<CatalogCourseResponse> courses =
                courseRepository.findAll().stream()
                        .filter(course -> !course.isFree())
                        .sorted(Comparator.comparing(Course::getName))
                        .flatMap(
                                course ->
                                        publishedVersion(course.getId()).stream()
                                                .map(version -> catalogCourse(course, version)))
                        .toList();
        return new PurchaseCatalogResponse(
                courses,
                pricing.getCurrency(),
                pricing.getVolumeDiscounts().stream().map(VolumeDiscountResponse::from).toList(),
                policy.getMinDays(),
                policy.getMaxDays(),
                policy.getInvitationDays(),
                policy.getMaxSeats());
    }

    @Transactional(readOnly = true)
    public PurchaseQuoteResponse quote(UUID courseId, int seats, int durationDays) {
        validateSize(seats, durationDays);
        Course course = findPurchasableCourse(courseId);
        int signCount = signCount(requirePublishedVersion(courseId));
        PurchasePricing.Quote quote = pricing.quote(signCount, durationDays, seats);
        return new PurchaseQuoteResponse(
                course.getId(),
                course.getName(),
                signCount,
                seats,
                durationDays,
                quote.listPricePerSeat(),
                quote.discountPercent(),
                quote.pricePerSeat(),
                quote.subtotal(),
                quote.discountAmount(),
                quote.totalAmount(),
                pricing.getCurrency());
    }

    @Transactional
    public PurchaseDetailResponse create(
            User actor, UUID organizationId, CreatePurchaseRequest request) {
        accessService.requireManage(actor, organizationId);
        Organization organization = findOrganization(organizationId);
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new InvalidInputException("The organization must be validated before buying");
        }

        Set<String> emails = normalizeEmails(request.emails());
        validateSize(emails.size(), request.durationDays());
        Course course = findPurchasableCourse(request.courseId());
        int signCount = signCount(requirePublishedVersion(course.getId()));
        requireNoLiveSeats(organizationId, course.getId(), emails, Instant.now());

        PurchasePricing.Quote quote =
                pricing.quote(signCount, request.durationDays(), emails.size());
        CoursePurchase purchase =
                purchaseRepository.save(
                        CoursePurchase.builder()
                                .organization(organization)
                                .course(course)
                                .createdBy(actor)
                                .seats(emails.size())
                                .durationDays(request.durationDays())
                                .signCount(signCount)
                                .listPricePerSeat(quote.listPricePerSeat())
                                .discountPercent(quote.discountPercent())
                                .pricePerSeat(quote.pricePerSeat())
                                .totalAmount(quote.totalAmount())
                                .currency(pricing.getCurrency())
                                .build());
        seatRepository.saveAll(
                emails.stream()
                        .map(
                                email ->
                                        PurchaseSeat.builder()
                                                .purchase(purchase)
                                                .email(email)
                                                .build())
                        .toList());
        return describe(purchase);
    }

    @Transactional(readOnly = true)
    public List<PurchaseResponse> list(User actor, UUID organizationId) {
        accessService.requireManage(actor, organizationId);
        Map<UUID, List<PurchaseSeat>> seatsByPurchase =
                seatRepository.findByPurchaseOrganizationId(organizationId).stream()
                        .collect(Collectors.groupingBy(seat -> seat.getPurchase().getId()));
        Instant now = Instant.now();
        return purchaseRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(
                        purchase ->
                                PurchaseResponse.from(
                                        purchase,
                                        seatsByPurchase.getOrDefault(purchase.getId(), List.of()),
                                        now))
                .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseDetailResponse get(User actor, UUID organizationId, UUID purchaseId) {
        accessService.requireManage(actor, organizationId);
        return describe(findPurchase(organizationId, purchaseId));
    }

    @Transactional
    public PurchaseDetailResponse cancel(User actor, UUID organizationId, UUID purchaseId) {
        accessService.requireManage(actor, organizationId);
        CoursePurchase purchase = findPurchase(organizationId, purchaseId);
        if (purchase.getStatus() != PurchaseStatus.PENDING_PAYMENT) {
            throw new InvalidInputException("Only unpaid purchases can be cancelled");
        }
        Instant now = Instant.now();
        purchase.setStatus(PurchaseStatus.CANCELLED);
        purchase.setCancelledAt(now);
        purchaseRepository.save(purchase);
        List<PurchaseSeat> seats = seatRepository.findByPurchaseIdOrderByEmailAsc(purchaseId);
        seats.forEach(seat -> seat.setStatus(SeatStatus.CANCELLED));
        seatRepository.saveAll(seats);
        return describe(purchase);
    }

    @Transactional
    public PurchaseDetailResponse updateSeatEmail(
            User actor,
            UUID organizationId,
            UUID purchaseId,
            UUID seatId,
            UpdateSeatEmailRequest request) {
        accessService.requireManage(actor, organizationId);
        CoursePurchase purchase = findPurchase(organizationId, purchaseId);
        PurchaseSeat seat = findSeat(purchaseId, seatId);
        Instant now = Instant.now();
        SeatStatus status = seat.statusAt(now);
        if (status != SeatStatus.PENDING_PAYMENT && status != SeatStatus.INVITED) {
            throw new InvalidInputException(
                    "The email can only be changed while the invitation is pending");
        }

        String email = normalizeEmail(request.email());
        if (email.equals(seat.getEmail())) {
            return describe(purchase);
        }
        boolean usedInPurchase =
                seatRepository.findByPurchaseIdOrderByEmailAsc(purchaseId).stream()
                        .anyMatch(other -> other.getEmail().equals(email));
        if (usedInPurchase) {
            throw new ResourceAlreadyInUseException(
                    "This purchase already has a seat for " + email);
        }
        requireNoLiveSeats(organizationId, purchase.getCourse().getId(), Set.of(email), now);

        seat.setEmail(email);
        if (status == SeatStatus.INVITED) {
            invitationService.invite(seat, now);
        } else {
            seatRepository.save(seat);
        }
        return describe(purchase);
    }

    @Transactional
    public PurchaseDetailResponse remindSeat(
            User actor, UUID organizationId, UUID purchaseId, UUID seatId) {
        accessService.requireManage(actor, organizationId);
        CoursePurchase purchase = findPurchase(organizationId, purchaseId);
        invitationService.remind(findSeat(purchaseId, seatId), Instant.now());
        return describe(purchase);
    }

    @Transactional(readOnly = true)
    public PurchaseDetailResponse describe(CoursePurchase purchase) {
        Instant now = Instant.now();
        List<PurchaseSeat> seats = seatRepository.findByPurchaseIdOrderByEmailAsc(purchase.getId());
        return new PurchaseDetailResponse(
                PurchaseResponse.from(purchase, seats, now),
                seats.stream().map(seat -> invitationService.toResponse(seat, now)).toList());
    }

    private void requireNoLiveSeats(
            UUID organizationId, UUID courseId, Set<String> emails, Instant now) {
        List<String> taken =
                seatRepository
                        .findCourseSeatsByEmails(
                                organizationId, courseId, emails, STORED_LIVE_STATUSES)
                        .stream()
                        .filter(seat -> seat.isLive(now))
                        .map(PurchaseSeat::getEmail)
                        .distinct()
                        .sorted()
                        .toList();
        if (!taken.isEmpty()) {
            throw new ResourceAlreadyInUseException(
                    "These emails already have a seat for this course: "
                            + String.join(", ", taken));
        }
    }

    private void validateSize(int seats, int durationDays) {
        if (seats < 1 || seats > policy.getMaxSeats()) {
            throw new InvalidInputException("Seats must be between 1 and " + policy.getMaxSeats());
        }
        if (!policy.isValidDuration(durationDays)) {
            throw new InvalidInputException(
                    "Duration must be between "
                            + policy.getMinDays()
                            + " and "
                            + policy.getMaxDays()
                            + " days");
        }
    }

    private static Set<String> normalizeEmails(List<String> rawEmails) {
        Set<String> emails = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (String raw : rawEmails) {
            String email = normalizeEmail(raw);
            if (!emails.add(email)) {
                duplicates.add(email);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new InvalidInputException(
                    "Each seat needs a different email; repeated: "
                            + String.join(", ", duplicates));
        }
        return emails;
    }

    private static String normalizeEmail(String raw) {
        return raw.trim().toLowerCase();
    }

    private Course findPurchasableCourse(UUID courseId) {
        Course course =
                courseRepository
                        .findById(courseId)
                        .orElseThrow(() -> new NotFoundException("Course not found"));
        if (course.isFree()) {
            throw new InvalidInputException("Free courses are open to everyone and need no seats");
        }
        return course;
    }

    private Optional<CourseVersion> publishedVersion(UUID courseId) {
        return courseVersionRepository.findByCourseIdAndStatus(courseId, VersionStatus.PUBLISHED);
    }

    private CourseVersion requirePublishedVersion(UUID courseId) {
        return publishedVersion(courseId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        "Active published version not found for course ID: "
                                                + courseId));
    }

    private CatalogCourseResponse catalogCourse(Course course, CourseVersion version) {
        List<CatalogModuleResponse> modules = signCatalog.modules(version.getId());
        return new CatalogCourseResponse(
                course.getId(),
                course.getCode(),
                course.getName(),
                course.getDescription(),
                course.getCoverUrl(),
                CourseSignCatalog.signCount(modules),
                modules);
    }

    /** The same count the catalog shows, so the price matches the list the admin checked. */
    private int signCount(CourseVersion version) {
        return CourseSignCatalog.signCount(signCatalog.modules(version.getId()));
    }

    private Organization findOrganization(UUID organizationId) {
        return organizationRepository
                .findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found"));
    }

    private CoursePurchase findPurchase(UUID organizationId, UUID purchaseId) {
        return purchaseRepository
                .findByIdAndOrganizationId(purchaseId, organizationId)
                .orElseThrow(() -> new NotFoundException("Purchase not found"));
    }

    private PurchaseSeat findSeat(UUID purchaseId, UUID seatId) {
        return seatRepository
                .findByIdAndPurchaseId(seatId, purchaseId)
                .orElseThrow(() -> new NotFoundException("Seat not found"));
    }
}
