package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.GemOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GemOrderRepository extends JpaRepository<GemOrder, UUID> {

    Optional<GemOrder> findByIdAndUserId(UUID id, UUID userId);

    /** Locks the row so the webhook and the app's sync never credit the same order twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM GemOrder o WHERE o.id = :id")
    Optional<GemOrder> findForUpdate(@Param("id") UUID id);
}
