package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserCourseEnrollmentRepository extends JpaRepository<UserCourseEnrollment, UUID> {

    Optional<UserCourseEnrollment> findByUserIdAndCourseVersionId(
            UUID userId, UUID courseVersionId);

    boolean existsByUserIdAndCourseVersionId(UUID userId, UUID courseVersionId);

    boolean existsByUserIdAndCourseVersionIdAndStatusNot(
            UUID userId, UUID courseVersionId, EnrollmentStatus status);
}
