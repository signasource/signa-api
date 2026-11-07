package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.CourseOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseOrderRepository extends JpaRepository<CourseOrder, UUID> {

    Optional<CourseOrder> findByIdAndUserId(UUID id, UUID userId);

    /** Locks the row so the webhook and the app's sync never grant the same order twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CourseOrder o WHERE o.id = :id")
    Optional<CourseOrder> findForUpdate(@Param("id") UUID id);
}
