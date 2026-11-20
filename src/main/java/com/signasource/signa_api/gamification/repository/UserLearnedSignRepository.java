package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.UserLearnedSign;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLearnedSignRepository extends JpaRepository<UserLearnedSign, UUID> {

    boolean existsByUserIdAndSign(UUID userId, String sign);

    boolean existsByUserIdAndSignAndCourseVersionId(UUID userId, String sign, UUID courseVersionId);

    long countByUserIdAndCourseVersionId(UUID userId, UUID courseVersionId);

    long countByUserIdAndCourseVersionIdAndLearnedAtBefore(
            UUID userId, UUID courseVersionId, Instant before);

    List<UserLearnedSign> findByUserOrderByLearnedAtDesc(User user, Pageable pageable);

    /** Follows a sign rename from the admin panel; learned rows store the meaning, not the id. */
    @Modifying
    @Query("UPDATE UserLearnedSign u SET u.sign = :to WHERE lower(u.sign) = lower(:from)")
    int renameSign(@Param("from") String from, @Param("to") String to);
}
