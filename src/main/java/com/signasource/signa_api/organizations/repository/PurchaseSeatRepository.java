package com.signasource.signa_api.organizations.repository;

import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseSeatRepository extends JpaRepository<PurchaseSeat, UUID> {
    @EntityGraph(attributePaths = {"user", "purchase"})
    List<PurchaseSeat> findByPurchaseIdOrderByEmailAsc(UUID purchaseId);

    @EntityGraph(attributePaths = {"purchase"})
    List<PurchaseSeat> findByPurchaseOrganizationId(UUID organizationId);

    @EntityGraph(attributePaths = {"purchase", "purchase.organization", "purchase.course"})
    Optional<PurchaseSeat> findByIdAndPurchaseId(UUID id, UUID purchaseId);

    @EntityGraph(attributePaths = {"purchase", "purchase.organization", "purchase.course"})
    Optional<PurchaseSeat> findByInvitationToken(String invitationToken);

    /**
     * Locks the seat being accepted. A refund locks every seat of its purchase first, so an
     * acceptance and a refund never interleave: whichever comes second sees the other's result.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"purchase", "purchase.organization", "purchase.course"})
    Optional<PurchaseSeat> findForUpdateByInvitationToken(String invitationToken);

    /** Every seat of a purchase, locked (see {@link #findForUpdateByInvitationToken}). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PurchaseSeat s WHERE s.purchase.id = :purchaseId ORDER BY s.email")
    List<PurchaseSeat> findForUpdateByPurchaseId(@Param("purchaseId") UUID purchaseId);

    boolean existsByInvitationToken(String invitationToken);

    @Query(
            "SELECT s FROM PurchaseSeat s JOIN FETCH s.purchase p "
                    + "WHERE p.organization.id = :organizationId AND p.course.id = :courseId "
                    + "AND s.email IN :emails AND s.status IN :statuses")
    List<PurchaseSeat> findCourseSeatsByEmails(
            @Param("organizationId") UUID organizationId,
            @Param("courseId") UUID courseId,
            @Param("emails") Collection<String> emails,
            @Param("statuses") Collection<SeatStatus> statuses);

    @Query(
            "SELECT s FROM PurchaseSeat s JOIN FETCH s.purchase p "
                    + "JOIN FETCH p.organization JOIN FETCH p.course "
                    + "WHERE s.status = com.signasource.signa_api.organizations.entity.SeatStatus.INVITED "
                    + "AND s.autoReminderSentAt IS NULL "
                    + "AND p.paidAt <= :paidBefore AND p.invitationDeadline > :now")
    List<PurchaseSeat> findDueForAutoReminder(
            @Param("paidBefore") Instant paidBefore, @Param("now") Instant now);

    List<PurchaseSeat> findByUserIdAndPurchaseOrganizationIdAndStatus(
            UUID userId, UUID organizationId, SeatStatus status);
}
