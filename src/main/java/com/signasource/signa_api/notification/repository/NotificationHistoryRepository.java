package com.signasource.signa_api.notification.repository;

import com.signasource.signa_api.notification.entity.NotificationHistory;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    Page<NotificationHistory> findByUserOrderBySentAtDesc(User user, Pageable pageable);

    List<NotificationHistory> findByUserAndReadFalseOrderBySentAtDesc(User user);

    long countByUserAndReadFalse(User user);

    Optional<NotificationHistory> findByIdAndUser(Long id, User user);

    @Modifying(clearAutomatically = true)
    @Query(
            "update NotificationHistory n set n.read = true, n.readAt = :now where n.user = :user and n.read = false")
    int markAllAsRead(@Param("user") User user, @Param("now") Instant now);
}
