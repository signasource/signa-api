package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.CourseOrder;
import com.signasource.signa_api.learning.entity.CourseOrderStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseOrderRepository extends JpaRepository<CourseOrder, UUID> {

    Optional<CourseOrder> findByIdAndUserId(UUID id, UUID userId);

    /** All orders for a user, newest first. */
    List<CourseOrder> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Every order in {@code status}, with its buyer and course loaded. */
    @EntityGraph(attributePaths = {"user", "course"})
    List<CourseOrder> findByStatus(CourseOrderStatus status);

    /** Locks the row so the webhook and the app's sync never grant the same order twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CourseOrder o WHERE o.id = :id")
    Optional<CourseOrder> findForUpdate(@Param("id") UUID id);
}
