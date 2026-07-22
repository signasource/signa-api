package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserCourseEnrollmentRepository extends JpaRepository<UserCourseEnrollment, UUID> {

    List<UserCourseEnrollment> findByUserId(UUID userId);

    Optional<UserCourseEnrollment> findByUserIdAndCourseVersionId(
            UUID userId, UUID courseVersionId);

    boolean existsByUserIdAndCourseVersionId(UUID userId, UUID courseVersionId);
}
