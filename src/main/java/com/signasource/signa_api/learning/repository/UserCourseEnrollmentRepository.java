package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserCourseEnrollmentRepository extends JpaRepository<UserCourseEnrollment, Long> {

    List<UserCourseEnrollment> findByUserId(Long userId);

    Optional<UserCourseEnrollment> findByUserIdAndCourseVersionId(
            Long userId, Long courseVersionId);

    boolean existsByUserIdAndCourseVersionId(Long userId, Long courseVersionId);
}
