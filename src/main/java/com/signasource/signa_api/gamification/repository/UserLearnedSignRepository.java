package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.UserLearnedSign;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLearnedSignRepository extends JpaRepository<UserLearnedSign, UUID> {

    boolean existsByUserIdAndSign(UUID userId, String sign);

    boolean existsByUserIdAndSignAndCourseVersionId(UUID userId, String sign, UUID courseVersionId);

    long countByUserIdAndCourseVersionId(UUID userId, UUID courseVersionId);
}
