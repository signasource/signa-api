package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.ForbiddenException;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.organizations.dto.CreatePurchaseRequest;
import com.signasource.signa_api.organizations.dto.PurchaseCatalogResponse;
import com.signasource.signa_api.organizations.dto.PurchaseDetailResponse;
import com.signasource.signa_api.organizations.dto.PurchaseQuoteResponse;
import com.signasource.signa_api.organizations.dto.PurchaseResponse;
import com.signasource.signa_api.organizations.dto.SeatResponse;
import com.signasource.signa_api.organizations.dto.UpdateSeatEmailRequest;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CoursePurchaseServiceTest {

    @Mock private CoursePurchaseRepository purchaseRepository;
    @Mock private PurchaseSeatRepository seatRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private CourseVersionRepository courseVersionRepository;
    @Mock private LessonBlockRepository lessonBlockRepository;
    @Mock private OrganizationAccessService accessService;
    @Mock private SeatInvitationService invitationService;

    private CoursePurchaseService service;
    private User actor;
    private Organization organization;
    private Course course;
    private CourseVersion version;

    @BeforeEach
    void setUp() {
        PurchasePolicy policy =
                new PurchasePolicy(
                        "ARS", new BigDecimal("5"), new BigDecimal("5000"), 15, 60, 10, 5, 24, 3);
        service =
                new CoursePurchaseService(
                        purchaseRepository,
                        seatRepository,
                        organizationRepository,
                        courseRepository,
                        courseVersionRepository,
                        lessonBlockRepository,
                        accessService,
                        invitationService,
                        policy);
        actor = new User();
        actor.setId(UUID.randomUUID());
        organization =
                Organization.builder()
                        .id(UUID.randomUUID())
                        .name("Hospital")
                        .status(OrganizationStatus.ACTIVE)
                        .build();
        course = Course.builder().id(UUID.randomUUID()).code("salud").name("LSA Salud").build();
        version = CourseVersion.builder().id(UUID.randomUUID()).course(course).build();
    }

    private void stubCourse() {
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(courseVersionRepository.findByCourseIdAndStatus(
                        course.getId(), VersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        when(lessonBlockRepository.countIntroducedSigns(version.getId())).thenReturn(100L);
    }

    private void stubOrganization() {
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
    }

    private CoursePurchase purchase(PurchaseStatus status) {
        return CoursePurchase.builder()
                .id(UUID.randomUUID())
                .organization(organization)
                .course(course)
                .seats(1)
                .durationDays(30)
                .pricePerSeat(BigDecimal.TEN)
                .totalAmount(BigDecimal.TEN)
                .currency("ARS")
                .status(status)
                .build();
    }

    @Test
    void shouldListOnlyPaidPublishedCoursesInTheCatalog() {
        Course free = Course.builder().id(UUID.randomUUID()).name("Base").isFree(true).build();
        Course draft = Course.builder().id(UUID.randomUUID()).name("Borrador").build();
        when(courseRepository.findAll()).thenReturn(List.of(free, course, draft));
        when(courseVersionRepository.findByCourseIdAndStatus(
                        course.getId(), VersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        when(courseVersionRepository.findByCourseIdAndStatus(
                        draft.getId(), VersionStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        when(lessonBlockRepository.countIntroducedSigns(version.getId())).thenReturn(42L);

        PurchaseCatalogResponse catalog = service.catalog();

        assertEquals(1, catalog.courses().size());
        assertEquals(42, catalog.courses().get(0).signCount());
        assertEquals(15, catalog.minDays());
        assertEquals(60, catalog.maxDays());
    }

    @Test
    void shouldQuoteBySignsDaysAndSeats() {
        stubCourse();

        PurchaseQuoteResponse quote = service.quote(course.getId(), 3, 30);

        assertEquals(new BigDecimal("15000.00"), quote.pricePerSeat());
        assertEquals(new BigDecimal("45000.00"), quote.totalAmount());
        assertEquals(100, quote.signCount());
    }

    @Test
    void shouldRejectADurationOutsideTheRange() {
        assertThrows(InvalidInputException.class, () -> service.quote(course.getId(), 1, 61));
        assertThrows(InvalidInputException.class, () -> service.quote(course.getId(), 1, 14));
    }

    @Test
    void shouldRejectTooManySeats() {
        assertThrows(InvalidInputException.class, () -> service.quote(course.getId(), 4, 30));
        assertThrows(InvalidInputException.class, () -> service.quote(course.getId(), 0, 30));
    }

    @Test
    void shouldRejectFreeCourses() {
        course.setFree(true);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        assertThrows(InvalidInputException.class, () -> service.quote(course.getId(), 1, 30));
    }

    @Test
    void shouldRejectUnknownCourses() {
        when(courseRepository.findById(course.getId())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.quote(course.getId(), 1, 30));
    }

    @Test
    void shouldCreateAnUnpaidPurchaseWithOneSeatPerEmail() {
        stubOrganization();
        stubCourse();
        when(purchaseRepository.save(any(CoursePurchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseDetailResponse detail =
                service.create(
                        actor,
                        organization.getId(),
                        new CreatePurchaseRequest(
                                course.getId(),
                                30,
                                List.of(" Ana@Hospital.com ", "bruno@hospital.com")));

        verify(accessService).requireManage(actor, organization.getId());
        ArgumentCaptor<CoursePurchase> saved = ArgumentCaptor.forClass(CoursePurchase.class);
        verify(purchaseRepository).save(saved.capture());
        assertEquals(2, saved.getValue().getSeats());
        assertEquals(new BigDecimal("30000.00"), saved.getValue().getTotalAmount());
        assertEquals(PurchaseStatus.PENDING_PAYMENT, saved.getValue().getStatus());
        assertEquals(100, saved.getValue().getSignCount());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PurchaseSeat>> seats = ArgumentCaptor.forClass(List.class);
        verify(seatRepository).saveAll(seats.capture());
        assertEquals(
                List.of("ana@hospital.com", "bruno@hospital.com"),
                seats.getValue().stream().map(PurchaseSeat::getEmail).toList());
        assertEquals(PurchaseStatus.PENDING_PAYMENT, detail.purchase().status());
    }

    @Test
    void shouldRejectRepeatedEmails() {
        stubOrganization();

        assertThrows(
                InvalidInputException.class,
                () ->
                        service.create(
                                actor,
                                organization.getId(),
                                new CreatePurchaseRequest(
                                        course.getId(), 30, List.of("a@x.com", "A@x.com"))));
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void shouldRejectEmailsThatAlreadyHoldALiveSeat() {
        stubOrganization();
        stubCourse();
        CoursePurchase earlier = purchase(PurchaseStatus.PAID);
        earlier.setInvitationDeadline(Instant.now().plus(5, ChronoUnit.DAYS));
        PurchaseSeat invited =
                PurchaseSeat.builder()
                        .purchase(earlier)
                        .email("a@x.com")
                        .status(SeatStatus.INVITED)
                        .build();
        when(seatRepository.findCourseSeatsByEmails(
                        eq(organization.getId()),
                        eq(course.getId()),
                        anyCollection(),
                        anyCollection()))
                .thenReturn(List.of(invited));

        ResourceAlreadyInUseException error =
                assertThrows(
                        ResourceAlreadyInUseException.class,
                        () ->
                                service.create(
                                        actor,
                                        organization.getId(),
                                        new CreatePurchaseRequest(
                                                course.getId(), 30, List.of("a@x.com"))));
        assertTrue(error.getMessage().contains("a@x.com"));
    }

    @Test
    void shouldAllowEmailsWhoseSeatAlreadyExpired() {
        stubOrganization();
        stubCourse();
        CoursePurchase earlier = purchase(PurchaseStatus.PAID);
        PurchaseSeat expired =
                PurchaseSeat.builder()
                        .purchase(earlier)
                        .email("a@x.com")
                        .status(SeatStatus.ACTIVE)
                        .accessExpiresAt(Instant.now().minusSeconds(60))
                        .build();
        when(seatRepository.findCourseSeatsByEmails(any(), any(), anyCollection(), anyCollection()))
                .thenReturn(List.of(expired));
        when(purchaseRepository.save(any(CoursePurchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(
                actor,
                organization.getId(),
                new CreatePurchaseRequest(course.getId(), 30, List.of("a@x.com")));

        verify(purchaseRepository).save(any(CoursePurchase.class));
    }

    @Test
    void shouldRequireAValidatedOrganization() {
        organization.setStatus(OrganizationStatus.PENDING);
        stubOrganization();

        assertThrows(
                InvalidInputException.class,
                () ->
                        service.create(
                                actor,
                                organization.getId(),
                                new CreatePurchaseRequest(course.getId(), 30, List.of("a@x.com"))));
    }

    @Test
    void shouldNotLetOutsidersBuyForAnOrganization() {
        doThrow(new ForbiddenException("no"))
                .when(accessService)
                .requireManage(actor, organization.getId());

        assertThrows(
                ForbiddenException.class,
                () ->
                        service.create(
                                actor,
                                organization.getId(),
                                new CreatePurchaseRequest(course.getId(), 30, List.of("a@x.com"))));
    }

    @Test
    void shouldListPurchasesWithTheirSeatCounts() {
        CoursePurchase purchase = purchase(PurchaseStatus.PENDING_PAYMENT);
        PurchaseSeat seat = PurchaseSeat.builder().purchase(purchase).email("a@x.com").build();
        when(seatRepository.findByPurchaseOrganizationId(organization.getId()))
                .thenReturn(List.of(seat));
        when(purchaseRepository.findByOrganizationIdOrderByCreatedAtDesc(organization.getId()))
                .thenReturn(List.of(purchase, purchase(PurchaseStatus.CANCELLED)));

        List<PurchaseResponse> list = service.list(actor, organization.getId());

        assertEquals(2, list.size());
        assertEquals(1, list.get(0).seatCounts().pendingPayment());
        assertEquals(0, list.get(1).seatCounts().pendingPayment());
    }

    @Test
    void shouldDescribeAPurchaseWithItsSeats() {
        CoursePurchase purchase = purchase(PurchaseStatus.PENDING_PAYMENT);
        PurchaseSeat seat = PurchaseSeat.builder().purchase(purchase).email("a@x.com").build();
        SeatResponse seatResponse =
                new SeatResponse(
                        null,
                        "a@x.com",
                        SeatStatus.PENDING_PAYMENT,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        null);
        when(purchaseRepository.findByIdAndOrganizationId(purchase.getId(), organization.getId()))
                .thenReturn(Optional.of(purchase));
        when(seatRepository.findByPurchaseIdOrderByEmailAsc(purchase.getId()))
                .thenReturn(List.of(seat));
        when(invitationService.toResponse(eq(seat), any())).thenReturn(seatResponse);

        PurchaseDetailResponse detail = service.get(actor, organization.getId(), purchase.getId());

        assertEquals(List.of(seatResponse), detail.seats());
    }

    @Test
    void shouldReturnNotFoundForAPurchaseOfAnotherOrganization() {
        UUID purchaseId = UUID.randomUUID();
        when(purchaseRepository.findByIdAndOrganizationId(purchaseId, organization.getId()))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service.get(actor, organization.getId(), purchaseId));
    }

    @Test
    void shouldCancelAnUnpaidPurchase() {
        CoursePurchase purchase = purchase(PurchaseStatus.PENDING_PAYMENT);
        PurchaseSeat seat = PurchaseSeat.builder().purchase(purchase).email("a@x.com").build();
        when(purchaseRepository.findByIdAndOrganizationId(purchase.getId(), organization.getId()))
                .thenReturn(Optional.of(purchase));
        when(seatRepository.findByPurchaseIdOrderByEmailAsc(purchase.getId()))
                .thenReturn(List.of(seat));

        service.cancel(actor, organization.getId(), purchase.getId());

        assertEquals(PurchaseStatus.CANCELLED, purchase.getStatus());
        assertEquals(SeatStatus.CANCELLED, seat.getStatus());
    }

    @Test
    void shouldNotCancelAPaidPurchase() {
        CoursePurchase purchase = purchase(PurchaseStatus.PAID);
        when(purchaseRepository.findByIdAndOrganizationId(purchase.getId(), organization.getId()))
                .thenReturn(Optional.of(purchase));

        assertThrows(
                InvalidInputException.class,
                () -> service.cancel(actor, organization.getId(), purchase.getId()));
    }

    private PurchaseSeat stubSeat(CoursePurchase purchase, SeatStatus status) {
        PurchaseSeat seat =
                PurchaseSeat.builder()
                        .id(UUID.randomUUID())
                        .purchase(purchase)
                        .email("old@x.com")
                        .status(status)
                        .build();
        when(purchaseRepository.findByIdAndOrganizationId(purchase.getId(), organization.getId()))
                .thenReturn(Optional.of(purchase));
        when(seatRepository.findByIdAndPurchaseId(seat.getId(), purchase.getId()))
                .thenReturn(Optional.of(seat));
        return seat;
    }

    @Test
    void shouldReinviteWhenChangingAPendingInvitationEmail() {
        CoursePurchase purchase = purchase(PurchaseStatus.PAID);
        Instant deadline = Instant.now().plus(3, ChronoUnit.DAYS);
        purchase.setInvitationDeadline(deadline);
        PurchaseSeat seat = stubSeat(purchase, SeatStatus.INVITED);

        service.updateSeatEmail(
                actor,
                organization.getId(),
                purchase.getId(),
                seat.getId(),
                new UpdateSeatEmailRequest(" New@X.com "));

        assertEquals("new@x.com", seat.getEmail());
        assertEquals(deadline, purchase.getInvitationDeadline());
        verify(invitationService).invite(eq(seat), any());
    }

    @Test
    void shouldJustSaveTheEmailBeforePayment() {
        CoursePurchase purchase = purchase(PurchaseStatus.PENDING_PAYMENT);
        PurchaseSeat seat = stubSeat(purchase, SeatStatus.PENDING_PAYMENT);

        service.updateSeatEmail(
                actor,
                organization.getId(),
                purchase.getId(),
                seat.getId(),
                new UpdateSeatEmailRequest("new@x.com"));

        verify(seatRepository).save(seat);
        verify(invitationService, never()).invite(any(), any());
    }

    @Test
    void shouldIgnoreAnUnchangedEmail() {
        CoursePurchase purchase = purchase(PurchaseStatus.PENDING_PAYMENT);
        PurchaseSeat seat = stubSeat(purchase, SeatStatus.PENDING_PAYMENT);

        service.updateSeatEmail(
                actor,
                organization.getId(),
                purchase.getId(),
                seat.getId(),
                new UpdateSeatEmailRequest("OLD@x.com"));

        verify(seatRepository, never()).save(any());
    }

    @Test
    void shouldNotChangeTheEmailOfAnAcceptedSeat() {
        CoursePurchase purchase = purchase(PurchaseStatus.PAID);
        PurchaseSeat seat = stubSeat(purchase, SeatStatus.ACTIVE);

        assertThrows(
                InvalidInputException.class,
                () ->
                        service.updateSeatEmail(
                                actor,
                                organization.getId(),
                                purchase.getId(),
                                seat.getId(),
                                new UpdateSeatEmailRequest("new@x.com")));
    }

    @Test
    void shouldNotChangeTheEmailToOneAlreadyInThePurchase() {
        CoursePurchase purchase = purchase(PurchaseStatus.PENDING_PAYMENT);
        PurchaseSeat seat = stubSeat(purchase, SeatStatus.PENDING_PAYMENT);
        PurchaseSeat other = PurchaseSeat.builder().purchase(purchase).email("taken@x.com").build();
        when(seatRepository.findByPurchaseIdOrderByEmailAsc(purchase.getId()))
                .thenReturn(List.of(seat, other));

        assertThrows(
                ResourceAlreadyInUseException.class,
                () ->
                        service.updateSeatEmail(
                                actor,
                                organization.getId(),
                                purchase.getId(),
                                seat.getId(),
                                new UpdateSeatEmailRequest("taken@x.com")));
    }

    @Test
    void shouldDelegateRemindersToTheInvitationService() {
        CoursePurchase purchase = purchase(PurchaseStatus.PAID);
        PurchaseSeat seat = stubSeat(purchase, SeatStatus.INVITED);

        service.remindSeat(actor, organization.getId(), purchase.getId(), seat.getId());

        verify(invitationService).remind(eq(seat), any());
    }

    @Test
    void shouldReturnNotFoundForAnUnknownSeat() {
        CoursePurchase purchase = purchase(PurchaseStatus.PAID);
        UUID seatId = UUID.randomUUID();
        when(purchaseRepository.findByIdAndOrganizationId(purchase.getId(), organization.getId()))
                .thenReturn(Optional.of(purchase));
        when(seatRepository.findByIdAndPurchaseId(seatId, purchase.getId()))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service.remindSeat(actor, organization.getId(), purchase.getId(), seatId));
    }
}
