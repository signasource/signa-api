package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.admin.dto.AdminPurchaseResponse;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationCourse;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.repository.OrganizationCourseRepository;
import com.signasource.signa_api.organizations.repository.PurchaseSeatRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchasePaymentServiceTest {

    @Mock private CoursePurchaseRepository purchaseRepository;
    @Mock private PurchaseSeatRepository seatRepository;
    @Mock private OrganizationCourseRepository organizationCourseRepository;
    @Mock private SeatInvitationService invitationService;
    @Mock private CoursePurchaseService purchaseService;

    private PurchasePaymentService service;
    private CoursePurchase purchase;
    private Organization organization;
    private Course course;

    @BeforeEach
    void setUp() {
        PurchasePolicy policy =
                new PurchasePolicy("ARS", BigDecimal.ONE, BigDecimal.ONE, 15, 60, 10, 5, 24, 500);
        service =
                new PurchasePaymentService(
                        purchaseRepository,
                        seatRepository,
                        organizationCourseRepository,
                        invitationService,
                        purchaseService,
                        policy);
        organization = Organization.builder().id(UUID.randomUUID()).name("Hospital").build();
        course = Course.builder().id(UUID.randomUUID()).name("LSA Salud").build();
        purchase =
                CoursePurchase.builder()
                        .id(UUID.randomUUID())
                        .organization(organization)
                        .course(course)
                        .seats(1)
                        .durationDays(30)
                        .pricePerSeat(BigDecimal.TEN)
                        .totalAmount(BigDecimal.TEN)
                        .currency("ARS")
                        .build();
    }

    @Test
    void shouldMarkPaidOpenTheWindowContractTheCourseAndInvite() {
        PurchaseSeat seat = PurchaseSeat.builder().purchase(purchase).email("a@x.com").build();
        when(purchaseRepository.findWithDetailsById(purchase.getId()))
                .thenReturn(Optional.of(purchase));
        when(organizationCourseRepository.existsByOrganizationIdAndCourseId(
                        organization.getId(), course.getId()))
                .thenReturn(false);
        when(seatRepository.findByPurchaseIdOrderByEmailAsc(purchase.getId()))
                .thenReturn(List.of(seat));

        service.confirmPayment(purchase.getId(), "mp-123");

        assertEquals(PurchaseStatus.PAID, purchase.getStatus());
        assertEquals("mp-123", purchase.getPaymentReference());
        assertEquals(
                Duration.ofDays(10),
                Duration.between(purchase.getPaidAt(), purchase.getInvitationDeadline()));
        verify(organizationCourseRepository).save(any(OrganizationCourse.class));
        verify(invitationService).invite(eq(seat), eq(purchase.getPaidAt()));
        verify(purchaseService).describe(purchase);
    }

    @Test
    void shouldNotContractTheCourseTwice() {
        when(purchaseRepository.findWithDetailsById(purchase.getId()))
                .thenReturn(Optional.of(purchase));
        when(organizationCourseRepository.existsByOrganizationIdAndCourseId(
                        organization.getId(), course.getId()))
                .thenReturn(true);
        when(seatRepository.findByPurchaseIdOrderByEmailAsc(purchase.getId()))
                .thenReturn(List.of());

        service.confirmPayment(purchase.getId(), null);

        verify(organizationCourseRepository, never()).save(any());
    }

    @Test
    void shouldBeIdempotentForAPaidPurchase() {
        purchase.setStatus(PurchaseStatus.PAID);
        when(purchaseRepository.findWithDetailsById(purchase.getId()))
                .thenReturn(Optional.of(purchase));

        service.confirmPayment(purchase.getId(), "mp-123");

        verify(invitationService, never()).invite(any(), any());
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void shouldRejectACancelledPurchase() {
        purchase.setStatus(PurchaseStatus.CANCELLED);
        when(purchaseRepository.findWithDetailsById(purchase.getId()))
                .thenReturn(Optional.of(purchase));

        assertThrows(
                InvalidInputException.class, () -> service.confirmPayment(purchase.getId(), null));
    }

    @Test
    void shouldReturnNotFoundForAnUnknownPurchase() {
        UUID unknown = UUID.randomUUID();
        when(purchaseRepository.findWithDetailsById(unknown)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.confirmPayment(unknown, null));
    }

    @Test
    void shouldListPurchasesForTheBackOffice() {
        when(purchaseRepository.findByStatusOrderByCreatedAtAsc(PurchaseStatus.PENDING_PAYMENT))
                .thenReturn(List.of(purchase));
        when(purchaseRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(seatRepository.findByPurchaseIdOrderByEmailAsc(purchase.getId()))
                .thenReturn(List.of());

        List<AdminPurchaseResponse> pending = service.listForAdmin(PurchaseStatus.PENDING_PAYMENT);
        List<AdminPurchaseResponse> all = service.listForAdmin(null);

        assertEquals("Hospital", pending.get(0).organizationName());
        assertEquals(0, all.size());
    }
}
